import copy,json,unittest
from pathlib import Path
from unittest.mock import patch
from run_release import validate,render,allocation,SERVICES
ROOT=Path(__file__).resolve().parents[2]
class ReleaseContractTest(unittest.TestCase):
    def setUp(self):
        self.config=json.loads((ROOT/'deploy/cloud-run/config.example.json').read_text())
        self.image='us-central1-docker.pkg.dev/replace-project-id/otel-payment-poc/payment@sha256:'+'a'*64
    def test_examples_are_blocked_from_deployment(self):
        with self.assertRaises(ValueError):validate(self.config)
        validate(self.config,allow_example=True)
    def test_mutable_image_tags_are_blocked(self):
        with self.assertRaises(ValueError):render(self.config,'payment-service',self.image.replace('@sha256:'+('a'*64),':latest'),self.image,'gh-1')
    def test_payment_preserves_workload_and_customer_identity_contract(self):
        manifest=render(self.config,'payment-service',self.image,self.image,'gh-1')
        containers=manifest['spec']['template']['spec']['containers']
        env={entry['name']:entry.get('value') for entry in containers[0]['env']}
        self.assertEqual(env['CLOUD_RUN_IAM_ENABLED'],'true')
        self.assertEqual(env['OTEL_EXPORTER_OTLP_ENDPOINT'],'http://127.0.0.1:4318')
        self.assertEqual(env['SPRING_RABBITMQ_PORT'],'5671')
        self.assertTrue(any(e['name']=='JWT_SECRET' and 'secretKeyRef' in e['valueFrom'] for e in containers[0]['env'] if 'valueFrom' in e))
        self.assertEqual(len(containers),2)
        self.assertEqual(manifest['spec']['template']['metadata']['annotations']['autoscaling.knative.dev/minScale'],'1')
    def test_frontend_has_no_jvm_collector_or_database_secrets(self):
        manifest=render(self.config,'frontend',self.image,self.image,'gh-1')
        containers=manifest['spec']['template']['spec']['containers']
        self.assertEqual(len(containers),1)
        self.assertFalse(any('valueFrom' in e for e in containers[0]['env']))
    def test_traffic_requires_resolved_revisions_and_preserves_split(self):
        service={'status':{'traffic':[{'revisionName':'old-a','percent':70},{'revisionName':'old-b','percent':30}]}}
        self.assertEqual(allocation(service),{'old-a':70,'old-b':30})
        with self.assertRaises(ValueError):allocation({'status':{'traffic':[{'latestRevision':True,'percent':100}]}})
    def test_rejects_credentials_in_url_and_unpinned_secrets(self):
        config=copy.deepcopy(self.config);config['urls']['frontend']='https://user:pass@replace-frontend.run.app'
        with self.assertRaises(ValueError):validate(config,allow_example=True)
        config=copy.deepcopy(self.config);config['secretVersion']='latest'
        with self.assertRaises(ValueError):validate(config,allow_example=True)
if __name__=='__main__':unittest.main()

class ReleaseRollbackTest(unittest.TestCase):
    def test_later_candidate_failure_restores_prior_promoted_service_split(self):
        import tempfile,os
        from run_release import release,CALLS
        config=json.loads((ROOT/'deploy/cloud-run/config.example.json').read_text())
        image='us-central1-docker.pkg.dev/replace-project-id/otel-payment-poc/payment@sha256:'+'a'*64
        states={service:{'status':{'url':config['urls'][service],'traffic':[{'revisionName':'old-a','percent':70},{'revisionName':'old-b','percent':30}]}} for service in SERVICES}
        calls=[]
        def fake_gcloud(conf,*args):
            calls.append(args)
            if args[2]=='get-iam-policy':
                members=['serviceAccount:'+value for value in config['serviceAccounts'].values()]
                if args[3].endswith(('frontend','api-gateway')):members.append('allUsers')
                return {'bindings':[{'role':'roles/run.invoker','members':members}]}
            if args[2]=='replace':return {}
            service=args[3].removeprefix(config['prefix']+'-')
            if args[2]=='describe':return copy.deepcopy(states[service])
            option=args[4]
            if option.startswith('--update-tags='):
                tag,revision=option.split('=',2)[1:]
                states[service]['status']['traffic'].append({'tag':tag,'revisionName':revision,'url':'https://candidate.run.app'})
            elif option.startswith('--to-revisions='):
                split=option.split('=',1)[1]
                states[service]['status']['traffic']=[{'revisionName':item.rsplit('=',1)[0],'percent':int(item.rsplit('=',1)[1])} for item in split.split(',')]
            return {}
        checks=[None,None,RuntimeError('second candidate unhealthy')]
        with tempfile.TemporaryDirectory() as directory:
            out=Path(directory);files={}
            for service in SERVICES:
                files[service]=out/(service+'.json');files[service].write_text(json.dumps(render(config,service,image,image,'gh-1')))
            with patch('run_release.gcloud',side_effect=fake_gcloud),patch('run_release.health',side_effect=checks),patch.dict(os.environ,{'GITHUB_RUN_ID':'100'}):
                with self.assertRaises(RuntimeError):release(config,files,out/'results.json')
            rollback=[call for call in calls if len(call)>4 and call[2]=='update-traffic' and call[4]=='--to-revisions=old-a=70,old-b=30']
            self.assertEqual(len(rollback),1)
            self.assertEqual(rollback[0][3],config['prefix']+'-'+SERVICES[0])
            self.assertEqual(json.loads((out/'pre-release.json').read_text())[SERVICES[0]],{'old-a':70,'old-b':30})
