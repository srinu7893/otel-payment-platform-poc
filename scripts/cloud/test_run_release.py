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
