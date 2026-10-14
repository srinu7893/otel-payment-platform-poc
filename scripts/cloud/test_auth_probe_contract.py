import json
from pathlib import Path
import unittest
from deploy_service import selected_manifest, REGISTRY, check_access, DEPLOYER
from run_release import PUBLIC

class AuthProbeDeploymentTest(unittest.TestCase):
    def test_auth_remains_private_with_http_probes_and_collector(self):
        config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/config.example.json').read_text())
        manifest=selected_manifest(config,'auth-service',REGISTRY+'/auth-service@sha256:'+'a'*64,REGISTRY+'/collector@sha256:'+'b'*64,'gh-1')
        self.assertNotIn('auth-service',PUBLIC)
        self.assertEqual(manifest['metadata']['annotations']['run.googleapis.com/invoker-iam-disabled'],'false')
        containers=manifest['spec']['template']['spec']['containers']
        self.assertEqual(containers[0]['startupProbe']['httpGet']['path'],'/actuator/health/liveness')
        self.assertEqual(containers[0]['livenessProbe']['httpGet']['path'],'/actuator/health/liveness')
        self.assertEqual(containers[1]['name'],'collector')
        config['serviceAccounts']={'auth-service':config['defaultServiceAccount']}
        policy={'bindings':[{'role':'roles/run.invoker','members':['serviceAccount:'+DEPLOYER,'serviceAccount:'+config['defaultServiceAccount']]}]}
        with self.assertRaisesRegex(ValueError,'enforce invoker IAM'):
            check_access(config,'auth-service',{'metadata':{'annotations':{'run.googleapis.com/invoker-iam-disabled':'true'}}},policy)
