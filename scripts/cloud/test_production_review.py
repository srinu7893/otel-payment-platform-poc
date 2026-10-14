import copy
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
from deploy_service import validate_selected, selected_manifest, discover, deploy, service_name, check_access, verify_runtime_permissions, REGISTRY
from run_release import SERVICES, PUBLIC, WORKERS, DATABASE, JWT, health, identity_token
from bootstrap_service import audit, commands, DEPLOYER

ROOT=Path(__file__).resolve().parents[2]

class ProductionContractTest(unittest.TestCase):
    def setUp(self):
        self.config=json.loads((ROOT/'deploy/cloud-run/config.bootstrap.example.json').read_text())
        self.config['serviceAccounts']={service:self.config['defaultServiceAccount'] for service in SERVICES}
        self.config.update(rabbitHost='broker.cloudamqp.com',rabbitVirtualHost='payments-vhost')
        self.collector=REGISTRY+'/collector@sha256:'+'b'*64

    def image(self,service):return REGISTRY+'/'+service_name(service)+'@sha256:'+'a'*64
    def partial(self,service):
        config={key:self.config[key] for key in ['project','region','repository']}
        config['serviceAccounts']={service:self.config['serviceAccounts'][service]}
        if service in DATABASE:config['databaseSecrets']=self.config['databaseSecrets']
        if service in JWT:config['jwtSecret']=self.config['jwtSecret']
        if service in DATABASE or service in JWT:config['secretVersion']='1'
        if service in WORKERS:
            config.update({key:self.config[key] for key in ['rabbitHost','rabbitPort','rabbitVirtualHost','rabbitSecrets']})
        return config

    def policy(self,service):
        members=['allUsers'] if service in PUBLIC else ['serviceAccount:'+self.config['serviceAccounts'][service], 'serviceAccount:'+DEPLOYER]
        return {'bindings':[{'role':'roles/run.invoker','members':members}]}

    def test_all_service_minimal_config_and_scaling_and_secrets(self):
        for service in SERVICES:
            with self.subTest(service=service):
                config=self.partial(service);validate_selected(config,service)
                m=selected_manifest(config,service,self.image(service),self.collector,'gh-1')
                template=m['spec']['template'];ann=template['metadata']['annotations']
                self.assertEqual(ann['autoscaling.knative.dev/minScale'],'1' if service in WORKERS else '0')
                self.assertEqual(ann['autoscaling.knative.dev/maxScale'],'1')
                self.assertEqual(ann['run.googleapis.com/cpu-throttling'],'true' if service=='frontend' else 'false')
                env={e['name']:e for e in template['spec']['containers'][0]['env']}
                self.assertEqual('JWT_SECRET' in env,service in JWT)
                self.assertEqual('SPRING_DATASOURCE_URL' in env,service in DATABASE)
                self.assertEqual('SPRING_RABBITMQ_USERNAME' in env,service in WORKERS)
                if service in WORKERS:
                    self.assertEqual(env['SPRING_RABBITMQ_VIRTUAL_HOST']['value'],'payments-vhost')
                    self.assertEqual(env['SPRING_RABBITMQ_SSL_ENABLED']['value'],'true')
                    self.assertIn('valueFrom',env['SPRING_RABBITMQ_PASSWORD'])
                # Offline minimal rendering has no discovered audiences; online deploy adds its canonical URL.
                if service in ['payment-service','gateway-service','api-gateway']:
                    online=copy.deepcopy(config);online['urls']={service:'https://canonical.run.app'}
                    online_env={e['name']:e for e in selected_manifest(online,service,self.image(service),self.collector,'gh-1')['spec']['template']['spec']['containers'][0]['env']}
                    self.assertEqual(online_env['CLOUD_RUN_IAM_ENABLED']['value'],'true')
                    self.assertEqual(online_env['CLOUD_RUN_AUDIENCES']['value'],'https://canonical.run.app')

    def test_discovery_overrides_display_url_without_mutating_input(self):
        for service in SERVICES:
            config=self.partial(service);config['urls']={service:'https://display.run.app'}
            original=copy.deepcopy(config)
            def fake(conf,*args):
                if args[3]!=service_name(service):raise subprocess.CalledProcessError(1,'gcloud')
                return {'spec':{'template':{'spec':{'serviceAccountName':self.config['serviceAccounts'][service]}}},'status':{'url':'https://canonical.run.app'}}
            with patch('deploy_service.gcloud',side_effect=fake):
                resolved,live,warnings=discover(config,service)
            self.assertEqual(resolved['urls'][service],'https://canonical.run.app')
            self.assertTrue(any('differs' in w for w in warnings));self.assertEqual(config,original)

    def test_missing_bootstrap_fails_clearly(self):
        with patch('deploy_service.gcloud',side_effect=subprocess.CalledProcessError(1,'gcloud')):
            with self.assertRaisesRegex(ValueError,'is not bootstrapped in'):
                discover(self.partial('customer-service'),'customer-service')

    def test_access_policy_for_every_service(self):
        for service in SERVICES:
            config=self.partial(service)
            check_access(config,service,{},self.policy(service))
            live={'metadata':{'annotations':{'run.googleapis.com/invoker-iam-disabled':'true'}}}
            if service in PUBLIC:check_access(config,service,live,{'bindings':[]})
            else:
                with self.assertRaisesRegex(ValueError,'enforce invoker IAM'):check_access(config,service,live,self.policy(service))
                for public in ['allUsers','allAuthenticatedUsers']:
                    with self.assertRaisesRegex(ValueError,'public invoker'):check_access(config,service,{}, {'bindings':[{'role':'roles/run.invoker','members':[public]}]})

    def test_public_health_never_impersonates_and_private_uses_canonical_audience(self):
        for service in SERVICES:
            config=self.partial(service);config['urls']={service:'https://canonical.run.app'}
            response=unittest.mock.MagicMock();response.__enter__.return_value=response
            response.status=200;response.read.return_value=b'ok' if service=='frontend' else b'{"status":"UP"}'
            with patch('run_release.subprocess.check_output',return_value='private-id-token') as token,patch('run_release.urllib.request.urlopen',return_value=response) as request:
                health(config,'https://tagged.run.app',service)
            if service in PUBLIC:
                token.assert_not_called();self.assertEqual(request.call_args.args[0].headers,{})
            else:
                self.assertIn('--audiences=https://canonical.run.app',token.call_args.args[0])
                self.assertEqual(request.call_args.args[0].headers['X-serverless-authorization'],'Bearer private-id-token')
            self.assertTrue(request.call_args.args[0].full_url.startswith('https://tagged.run.app/'))

    def test_token_failure_names_required_role(self):
        config=self.partial('customer-service');config['urls']={'customer-service':'https://canonical.run.app'}
        with patch('run_release.subprocess.check_output',side_effect=subprocess.CalledProcessError(1,'gcloud')):
            with self.assertRaisesRegex(ValueError,'roles/iam.serviceAccountTokenCreator'):identity_token(config,'customer-service')

    def test_all_workflows_keep_manual_main_ci_wif_and_shared_selection(self):
        mapping={'auth':'auth-service','customer':'customer-service','bank':'mock-bank-service','gateway':'gateway-service','payment':'payment-service','notification':'notification-service','api-gateway':'api-gateway','frontend':'frontend'}
        for suffix,service in mapping.items():
            text=(ROOT/'.github/workflows'/('deploy-'+suffix+'.yml')).read_text()
            self.assertIn('on:\n  workflow_dispatch:',text)
            self.assertNotIn('\n  push:',text);self.assertNotIn('\n  pull_request:',text)
            self.assertIn("if: github.ref == 'refs/heads/main'",text)
            self.assertIn('SERVICE: '+service,text)
            self.assertIn('id-token: write',text);self.assertIn('environment: gcp-poc',text)
            self.assertIn('google-github-actions/auth@',text)
            for step in ['ci','validate','publish','deploy']:
                self.assertIn('run: bash scripts/cloud/workflow_service.sh '+step,text)
            self.assertIn('if: always()',text);self.assertIn('actions/upload-artifact@',text)
            cloudbuild=(ROOT/('cloudbuild-'+suffix+'.yaml')).read_text()
            self.assertIn(service+'/Dockerfile',cloudbuild)
            if service!='frontend':self.assertIn("'-pl', '"+service+"'",cloudbuild)

    def test_traffic_types_fail_with_local_diagnostics(self):
        from run_release import normalize_traffic
        for entry in [{'revisionName':'old','percent':'100'}, {'revisionName':'old','percent':-1},
                      {'revisionName':123,'percent':100}, {'latestRevision':'true','percent':100},
                      {'revisionName':'one','revision_name':'two','percent':100}]:
            with self.assertRaises(ValueError):normalize_traffic([entry])

    def test_act_as_preflight_fails_before_deployment(self):
        config=self.partial('frontend')
        with patch('deploy_service.subprocess.check_output',return_value='access-token'),patch('deploy_service.urllib.request.urlopen',return_value=io.BytesIO(b'{"permissions":[]}')):
            with self.assertRaisesRegex(ValueError,'roles/iam.serviceAccountUser'):verify_runtime_permissions(config,'frontend')

    def test_all_service_bootstrap_audit_and_commands_do_not_execute_gcloud(self):
        for service in SERVICES:
            config=self.partial(service);config['urls']={service:'https://canonical.run.app'}
            live=selected_manifest(config,service,self.image(service),self.collector,'bootstrap-1')
            self.assertEqual(audit(config,service,live,self.policy(service),self.collector)['status'],'PASS')
            broken=copy.deepcopy(live);broken['spec']['template']['spec']['containers'][0]['ports']=[{'containerPort':9090}]
            with self.assertRaisesRegex(ValueError,'8080'):audit(config,service,broken,self.policy(service),self.collector)
            with tempfile.TemporaryDirectory() as directory,patch('bootstrap_service.gcloud') as cloud:
                result=commands(config,service,self.image(service),self.collector,Path(directory))
                cloud.assert_not_called()
                self.assertTrue(any('replace' in command for command in result))
                self.assertEqual(any('--member=allUsers' in command for command in result),service in PUBLIC)

    def test_bootstrap_accepts_api_quantity_and_port_name_normalization(self):
        config=self.partial('customer-service');config['urls']={'customer-service':'https://canonical.run.app'}
        live=selected_manifest(config,'customer-service',self.image('customer-service'),self.collector,'bootstrap-1')
        app=live['spec']['template']['spec']['containers'][0]
        app['resources']['limits']['cpu']='1000m';app['ports'][0]['name']='http1'
        app['startupProbe']['httpGet']['scheme']='HTTP'
        audit(config,'customer-service',live,self.policy('customer-service'),self.collector)

    def test_bootstrap_rejects_identity_scaling_secret_and_exporter_mismatches(self):
        config=self.partial('customer-service');config['urls']={'customer-service':'https://canonical.run.app'}
        original=selected_manifest(config,'customer-service',self.image('customer-service'),self.collector,'bootstrap-1')
        for problem in ['identity','scale','secret','exporter','cpu']:
            live=copy.deepcopy(original);template=live['spec']['template'];app=template['spec']['containers'][0]
            if problem=='identity':template['spec']['serviceAccountName']='wrong'
            if problem=='scale':template['metadata']['annotations']['autoscaling.knative.dev/maxScale']='10'
            if problem=='secret':next(e for e in app['env'] if 'valueFrom' in e)['valueFrom']['secretKeyRef']['key']='latest'
            if problem=='exporter':next(e for e in app['env'] if e['name']=='OTEL_TRACES_EXPORTER')['value']='none'
            if problem=='cpu':app['resources']['limits']['cpu']='2'
            with self.assertRaises(ValueError):audit(config,'customer-service',live,self.policy('customer-service'),self.collector)

    def test_all_service_promotion_and_rollback_touch_only_selected_service(self):
        for service in SERVICES:
            for fail in [False,True]:
                with self.subTest(service=service,post_promotion_failure=fail):
                    config=self.partial(service);original=copy.deepcopy(config);calls=[]
                    state={'spec':{'template':{'spec':{'serviceAccountName':self.config['serviceAccounts'][service]}}},'status':{'url':'https://canonical.run.app','traffic':[{'revisionName':'old-a','latestRevision':True,'percent':70},{'revisionName':'old-b','percent':30}]}}
                    def fake(conf,*args):
                        calls.append(args)
                        if args[2]=='describe':
                            if args[3]!=service_name(service):raise subprocess.CalledProcessError(1,'gcloud')
                            return copy.deepcopy(state)
                        if args[2]=='get-iam-policy':return self.policy(service)
                        if args[2]=='replace':
                            m=json.loads(Path(args[3]).read_text());self.assertEqual(m['spec']['traffic'],[{'percent':70,'revisionName':'old-a'},{'percent':30,'revisionName':'old-b'}]);return {}
                        if args[2]=='update-traffic':
                            option=args[4]
                            if option.startswith('--update-tags='):
                                tag,rev=option.split('=',2)[1:];state['status']['traffic'].append({'tag':tag,'revisionName':rev,'url':'https://tag.run.app'})
                            elif option.startswith('--to-revisions='):
                                state['status']['traffic']=[{'revisionName':item.rsplit('=',1)[0],'percent':int(item.rsplit('=',1)[1])} for item in option.split('=',1)[1].split(',')]
                        return {}
                    with tempfile.TemporaryDirectory() as directory,patch('deploy_service.gcloud',side_effect=fake),patch('deploy_service.verify_runtime_permissions'),patch('deploy_service.health',side_effect=[None,RuntimeError('failed canonical health')] if fail else None):
                        if fail:
                            with self.assertRaises(RuntimeError):deploy(config,service,self.image(service),self.collector,'gh-1',Path(directory))
                        else:deploy(config,service,self.image(service),self.collector,'gh-1',Path(directory))
                    self.assertEqual(config,original)
                    self.assertTrue(all(a[3]==service_name(service) for a in calls if a[2] not in ['describe','replace']))
                    allocation={t['revisionName']:t['percent'] for t in state['status']['traffic'] if t.get('percent')}
                    self.assertEqual(allocation,{'old-a':70,'old-b':30} if fail else {service_name(service)+'-gh-1':100})
