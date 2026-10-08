import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from deploy_service import PROJECT, REGISTRY, service_name, selected_manifest, deploy, validate_selected
from run_release import SERVICES

class IndependentDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/service-config.example.json').read_text())
        self.config['network']='poc-network';self.config['subnetwork']='poc-subnet';self.config['rabbitHost']='broker.internal'
        self.config['urls']={s:'https://'+s+'.run.app' for s in SERVICES}
        self.collector=REGISTRY+'/collector@sha256:'+'b'*64
        self.image=REGISTRY+'/auth-service@sha256:'+'a'*64

    def partial(self, service):
        config = {key: copy.deepcopy(self.config[key]) for key in ['project', 'region', 'repository']}
        config['urls'] = {service: self.config['urls'][service]}
        config['serviceAccounts'] = {service: self.config['serviceAccounts'][service]}
        return config

    def test_customer_only_neon_example(self):
        config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/config.example.json').read_text())
        manifest=selected_manifest(config,'customer-service',self.image,self.collector,'gh-1')
        env=manifest['spec']['template']['spec']['containers'][0]['env']
        refs={e['name']:e['valueFrom']['secretKeyRef'] for e in env if 'valueFrom' in e}
        self.assertEqual(refs, {name: {'name': secret, 'key':'1'} for name,secret in [
            ('SPRING_DATASOURCE_URL','neon-jdbc-url'),('SPRING_DATASOURCE_USERNAME','neon-username'),('SPRING_DATASOURCE_PASSWORD','neon-password')]})
        self.assertNotIn('run.googleapis.com/network-interfaces',manifest['spec']['template']['metadata']['annotations'])
        self.assertEqual(set(config['urls']),{'customer-service'})

    def test_customer_only_rollout_and_candidate_failure(self):
        config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/config.example.json').read_text())
        for failure in [False, True]:
            with self.subTest(candidate_failure=failure):
                state={'status':{'url':config['urls']['customer-service'],'traffic':[{'revisionName':'old-customer','latestRevision':True,'percent':100}]}}
                calls=[]
                def fake(conf,*args):
                    calls.append(args)
                    if args[2]=='get-iam-policy':
                        return {'bindings':[{'role':'roles/run.invoker','members':['serviceAccount:'+config['serviceAccounts']['customer-service']]}]}
                    if args[2]=='describe':return copy.deepcopy(state)
                    if args[2]=='replace':
                        manifest=json.loads(Path(args[3]).read_text())
                        self.assertEqual(manifest['spec']['traffic'],[{'revisionName':'old-customer','percent':100}])
                        return {}
                    option=args[4]
                    if option.startswith('--update-tags='):
                        tag,revision=option.split('=',2)[1:]
                        state['status']['traffic'].append({'tag':tag,'revisionName':revision,'url':'https://candidate.run.app'})
                    elif option.startswith('--to-revisions='):
                        revision,percent=option.split('=',1)[1].rsplit('=',1)
                        state['status']['traffic']=[{'revisionName':revision,'percent':int(percent)}]
                    elif option.startswith('--remove-tags='):
                        state['status']['traffic']=[t for t in state['status']['traffic'] if not t.get('tag')]
                    return {}
                with tempfile.TemporaryDirectory() as directory,patch('deploy_service.gcloud',side_effect=fake),patch('deploy_service.health',side_effect=RuntimeError('candidate unhealthy') if failure else None):
                    if failure:
                        with self.assertRaises(RuntimeError):deploy(config,'customer-service',self.image,self.collector,'gh-1',Path(directory))
                        self.assertFalse(any(len(a)>4 and a[4].startswith('--to-revisions=') for a in calls))
                    else:
                        deploy(config,'customer-service',self.image,self.collector,'gh-1',Path(directory))
                        self.assertEqual(json.loads((Path(directory)/'result.json').read_text())['status'],'PASS')
                self.assertEqual(state['status']['traffic'],[{'revisionName':'old-customer','latestRevision':True,'percent':100}] if failure else [{'revisionName':'customer-service-gh-1','percent':100}])
                self.assertTrue(all(a[3]=='customer-service' for a in calls if a[2]!='replace'))

    def test_every_service_accepts_only_its_own_configuration(self):
        from run_release import DATABASE, JWT
        for service in SERVICES:
            with self.subTest(service=service):
                config=self.partial(service)
                if service in DATABASE:config['databaseSecrets']=self.config['databaseSecrets']
                if service in JWT:config['jwtSecret']=self.config['jwtSecret']
                if service in DATABASE or service in JWT:config['secretVersion']='1'
                if service in {'payment-service','notification-service'}:
                    config.update(rabbitHost='broker.internal',rabbitSecrets=self.config['rabbitSecrets'])
                selected_manifest(config,service,self.image,self.collector,'gh-1')

    def test_missing_selected_fields_and_placeholder_secrets_are_rejected(self):
        config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/config.example.json').read_text())
        for key in ['project','region','repository','urls','serviceAccounts','databaseSecrets','secretVersion']:
            with self.subTest(key=key):
                broken=copy.deepcopy(config);broken.pop(key)
                with self.assertRaises(ValueError):validate_selected(broken,'customer-service')
        for key in ['url','username','password']:
            for invalid in [None,'replace-secret','']:
                with self.subTest(key=key, invalid=invalid):
                    broken=copy.deepcopy(config);broken['databaseSecrets'][key]=invalid
                    with self.assertRaises(ValueError):validate_selected(broken,'customer-service')
        for invalid in ['latest',0,'0','replace-1']:
            broken=copy.deepcopy(config);broken['secretVersion']=invalid
            with self.assertRaises(ValueError):validate_selected(broken,'customer-service')

    def test_selected_identity_and_url_are_strict(self):
        config=self.partial('gateway-service')
        for url in ['replace-url','https://replace-gateway.run.app','http://gateway.run.app','https://u:p@gateway.run.app','https://gateway.run.app/path','https://gateway.run.app?x=1']:
            broken=copy.deepcopy(config);broken['urls']['gateway-service']=url
            with self.assertRaises(ValueError):validate_selected(broken,'gateway-service')
        for account in ['replace-account','runtime@other-project.iam.gserviceaccount.com']:
            broken=copy.deepcopy(config);broken['serviceAccounts']['gateway-service']=account
            with self.assertRaises(ValueError):validate_selected(broken,'gateway-service')

    def test_rabbit_and_jwt_only_required_by_consumers(self):
        from run_release import DATABASE, JWT
        for service in SERVICES:
            config=self.partial(service)
            if service in DATABASE:config['databaseSecrets']=self.config['databaseSecrets']
            config['secretVersion']='1'
            if service in JWT:
                with self.assertRaises(ValueError):validate_selected(config,service)
                config['jwtSecret']='jwt-key'
            if service in {'payment-service','notification-service'}:
                with self.assertRaises(ValueError):validate_selected(config,service)
                config.update(rabbitHost='broker.internal',rabbitSecrets=self.config['rabbitSecrets'])
                for missing in ['rabbitHost','rabbitSecrets']:
                    broken=copy.deepcopy(config);broken.pop(missing)
                    with self.assertRaises(ValueError):validate_selected(broken,service)
            validate_selected(config,service)

    def test_unrelated_configuration_does_not_block_customer(self):
        config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/config.example.json').read_text())
        config.update(jwtSecret='replace-jwt',rabbitHost='replace-broker')
        config['urls']['payment-service']='replace-url'
        config['serviceAccounts']['auth-service']=config['serviceAccounts']['customer-service']
        selected_manifest(config,'customer-service',self.image,self.collector,'gh-1')

    def test_optional_dependencies_and_private_network_are_validated(self):
        config=self.partial('gateway-service')
        config['urls']['mock-bank-service']='https://bank.run.app'
        manifest=selected_manifest(config,'gateway-service',self.image,self.collector,'gh-1')
        env={e['name']:e.get('value') for e in manifest['spec']['template']['spec']['containers'][0]['env']}
        self.assertEqual(env['CLIENTS_BANK_BASE_URL'],'https://bank.run.app')
        self.assertEqual(env['CLOUD_RUN_AUDIENCES'],'https://bank.run.app')
        config['urls']['mock-bank-service']='https://replace-bank.run.app'
        with self.assertRaises(ValueError):validate_selected(config,'gateway-service')
        config['urls'].pop('mock-bank-service');config['network']='poc-network'
        with self.assertRaises(ValueError):validate_selected(config,'gateway-service')
        config['subnetwork']='poc-subnet'
        selected_manifest(config,'gateway-service',self.image,self.collector,'gh-1')

    def test_every_service_has_dedicated_name_and_no_reserved_port(self):
        for service in SERVICES:
            manifest=selected_manifest(self.config,service,self.image,self.collector,'gh-1')
            self.assertEqual(manifest['metadata']['name'],service_name(service))
            containers=manifest['spec']['template']['spec']['containers']
            self.assertEqual(len(containers),1 if service=='frontend' else 2)
            self.assertFalse(any(e['name']=='PORT' for e in containers[0]['env']))
            self.assertEqual(containers[0]['ports'][0]['containerPort'],8080)

    def test_registry_and_collector_digest_are_required(self):
        with self.assertRaises(ValueError):selected_manifest(self.config,'auth-service',self.image,'collector:latest','gh-1')
        config=copy.deepcopy(self.config);config['project']='other-project'
        with self.assertRaises(ValueError):selected_manifest(config,'frontend',self.image,'','gh-1')

    def test_failure_after_promotion_rolls_back_only_selected_service(self):
        # No future caller account is available during this release.
        self.config['serviceAccounts']={'auth-service':self.config['serviceAccounts']['auth-service']}
        calls=[];state={'status':{'url':self.config['urls']['auth-service'],'traffic':[{'revisionName':'auth-old','latest_revision':True,'percent':100}]}}
        def fake(config,*args):
            calls.append(args)
            if args[2]=='get-iam-policy':
                return {'bindings':[{'role':'roles/run.invoker','members':['serviceAccount:'+a for a in self.config['serviceAccounts'].values()]}]}
            if args[2]=='describe':return copy.deepcopy(state)
            if args[2]=='update-traffic':
                option=args[4]
                if option.startswith('--update-tags='):
                    tag,revision=option.split('=',2)[1:]
                    state['status']['traffic'].append({'tag':tag,'revisionName':revision,'url':'https://candidate.run.app'})
                if option.startswith('--to-revisions='):
                    revision,percentage=option.split('=',1)[1].rsplit('=',1)
                    state['status']['traffic']=[{'revisionName':revision,'percent':int(percentage)}]
            return {}
        with tempfile.TemporaryDirectory() as directory,patch('deploy_service.gcloud',side_effect=fake),patch('deploy_service.health',side_effect=[None,RuntimeError('readiness failure')]):
            with self.assertRaises(RuntimeError):deploy(self.config,'auth-service',self.image,self.collector,'gh-1',Path(directory))
        self.assertEqual(state['status']['traffic'],[{'revisionName':'auth-old','percent':100}])
        self.assertTrue(all(a[3]=='auth-service' for a in calls if a[2]!='replace'))
        self.assertEqual(sum(a[2]=='replace' for a in calls),1)

    def test_private_service_public_policy_blocks_before_replacement(self):
        def fake(config,*args):
            if args[2]=='describe':return {'status':{'url':config['urls']['auth-service']}}
            return {'bindings':[{'role':'roles/run.invoker','members':['allUsers']}]}
        with tempfile.TemporaryDirectory() as directory,patch('deploy_service.gcloud',side_effect=fake) as cloud:
            with self.assertRaises(ValueError):deploy(self.config,'auth-service',self.image,self.collector,'gh-1',Path(directory))
            self.assertFalse(any(c.args[3]=='replace' for c in cloud.call_args_list))
