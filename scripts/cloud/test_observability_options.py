import copy,json,unittest
from pathlib import Path
from unittest.mock import patch
from deploy_service import validate_selected,selected_manifest,REGISTRY,discover

class ObservabilityOptionsTest(unittest.TestCase):
    def setUp(self):self.config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/config.example.json').read_text())
    def manifest(self,service='api-gateway'):
        return selected_manifest(self.config,service,REGISTRY+'/'+service+'@sha256:'+'a'*64,REGISTRY+'/collector@sha256:'+'b'*64,'gh-1')
    def test_warm_profile_is_opt_in_and_keeps_workers_running(self):
        self.assertEqual(self.manifest()['spec']['template']['metadata']['annotations']['autoscaling.knative.dev/minScale'],'0')
        self.config['warmServices']=['api-gateway']
        self.assertEqual(self.manifest()['spec']['template']['metadata']['annotations']['autoscaling.knative.dev/minScale'],'1')
        self.config['warmServices']=['unknown']
        with self.assertRaises(ValueError):self.manifest()
    def test_durable_overlay_uses_only_secret_reference_and_explicit_https(self):
        self.config['durableCollector']={'endpoint':'https://collector.example.com:4318','tokenSecret':'otel-ingest-token'}
        manifest=self.manifest();sidecar=manifest['spec']['template']['spec']['containers'][-1]
        self.assertIn('--config=/etc/otelcol-contrib/durable.yaml',sidecar['args'])
        token=next(e for e in sidecar['env'] if e['name']=='DURABLE_COLLECTOR_TOKEN')
        self.assertEqual(token['valueFrom']['secretKeyRef']['name'],'otel-ingest-token')
        for endpoint in ['http://collector.example.com','https://user:password@collector.example.com','https://replace-host','https://collector.example.com/path']:
            self.config['durableCollector']['endpoint']=endpoint
            with self.subTest(endpoint=endpoint),self.assertRaises(ValueError):self.manifest()
    def test_gateway_discovers_all_advertised_frontend_origins(self):
        account=self.config['defaultServiceAccount']
        def cloud(config,*args):
            service=args[3]
            live={'status':{'url':'https://'+service+'.run.app'},'spec':{'template':{'spec':{'serviceAccountName':account}}}}
            if service=='payment-frontend':live['metadata']={'annotations':{'run.googleapis.com/urls':'["https://numbered-frontend.us-central1.run.app"]'}}
            return live
        with patch('deploy_service.gcloud',side_effect=cloud):resolved,_,_=discover(self.config,'api-gateway')
        self.assertIn('https://numbered-frontend.us-central1.run.app',resolved['browserOrigins'])
        self.config=resolved;env=self.manifest()['spec']['template']['spec']['containers'][0]['env']
        self.assertIn('https://payment-frontend.run.app',next(e['value'] for e in env if e['name']=='TELEMETRY_RUM_ALLOWED_ORIGINS'))

if __name__=='__main__':unittest.main()
