import copy
import json
import tempfile
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch
from deploy_service import (runtime_account, validate_selected, selected_manifest, discover,
                            check_access, live_properties, DEPLOYER, REGISTRY, deploy)
from run_release import SERVICES

ROOT=Path(__file__).resolve().parents[2]

class DynamicConfigurationTest(unittest.TestCase):
    def setUp(self):
        self.config=json.loads((ROOT/'deploy/cloud-run/config.example.json').read_text())
        self.account=self.config['defaultServiceAccount']

    def live(self, account=None):
        return {'spec':{'template':{'metadata':{'annotations':{'autoscaling.knative.dev/minScale':'0','autoscaling.knative.dev/maxScale':'1','run.googleapis.com/cpu-throttling':'false'}},
            'spec':{'serviceAccountName':account or self.account,'containers':[{'name':'app','ports':[{'containerPort':8080}], 'env':[{'name':'PASSWORD','value':'do-not-record'},{'name':'SPRING_DATASOURCE_URL','valueFrom':{'secretKeyRef':{'name':'neon-jdbc-url','key':'1'}}}]}]}}},
            'status':{'url':'https://actual.run.app','latestReadyRevisionName':'old-customer','traffic':[{'revisionName':'old-customer','latestRevision':True,'percent':100}]}}

    def policy(self):
        return {'bindings':[{'role':'roles/run.invoker','members':['serviceAccount:'+self.account,'serviceAccount:'+DEPLOYER]}]}

    def test_stable_example_validates_all_services_without_urls_or_accounts_map(self):
        for service in SERVICES:
            with self.subTest(service=service):
                validate_selected(self.config,service)
                m=selected_manifest(self.config,service,REGISTRY+'/'+service+'@sha256:'+'a'*64,REGISTRY+'/collector@sha256:'+'b'*64,'gh-1')
                self.assertEqual(m['spec']['template']['spec']['serviceAccountName'],self.account)
        self.assertNotIn('urls',self.config);self.assertNotIn('serviceAccounts',self.config)

    def test_retired_full_stack_validator_accepts_stable_format_without_mutation(self):
        from run_release import validate
        original=copy.deepcopy(self.config)
        normalized=validate(self.config)
        self.assertEqual(normalized['urls'],{})
        self.assertEqual(set(normalized['serviceAccounts']),set(SERVICES))
        self.assertEqual(self.config,original)

    def test_selected_account_override_has_precedence(self):
        config=copy.deepcopy(self.config)
        override='dedicated-bank@'+config['project']+'.iam.gserviceaccount.com'
        config['serviceAccounts']={'mock-bank-service':override,'unrelated':'invalid'}
        self.assertEqual(runtime_account(config,'mock-bank-service'),override)
        self.assertEqual(runtime_account(config,'customer-service'),self.account)
        with patch('deploy_service.gcloud',return_value=self.live(override)):
            resolved,_,_=discover(config,'mock-bank-service')
        self.assertEqual(resolved['serviceAccounts']['mock-bank-service'],override)

    def test_missing_runtime_configuration_is_explicit(self):
        config=copy.deepcopy(self.config);config.pop('defaultServiceAccount')
        with self.assertRaisesRegex(ValueError,'or defaultServiceAccount'):validate_selected(config,'customer-service')

    def test_describe_permission_error_is_not_misreported_as_missing_bootstrap(self):
        error=subprocess.CalledProcessError(1,'gcloud',stderr='PERMISSION_DENIED: run.services.get denied')
        with patch('deploy_service.gcloud',side_effect=error):
            with self.assertRaisesRegex(ValueError,'roles/run.viewer or roles/run.admin'):discover(self.config,'customer-service')

    def test_runtime_mismatch_fails_before_any_write(self):
        with patch('deploy_service.gcloud',return_value=self.live('wrong@other.iam.gserviceaccount.com')) as cloud:
            with tempfile.TemporaryDirectory() as directory:
                with self.assertRaisesRegex(ValueError,'Runtime account mismatch'):
                    deploy(self.config,'customer-service',REGISTRY+'/customer-service@sha256:'+'a'*64,REGISTRY+'/collector@sha256:'+'b'*64,'gh-1',Path(directory))
            self.assertTrue(all(c.args[3]=='describe' for c in cloud.call_args_list))

    def test_missing_each_private_invoker_binding_fails(self):
        for missing in [self.account,DEPLOYER]:
            policy=self.policy();policy['bindings'][0]['members'].remove('serviceAccount:'+missing)
            with self.assertRaisesRegex(ValueError,'roles/run.invoker'):
                check_access(self.config,'customer-service',self.live(),policy)

    def test_live_properties_capture_references_without_plaintext_values(self):
        properties=live_properties(self.live(),self.policy())
        self.assertEqual(properties['runtimeServiceAccount'],self.account)
        self.assertEqual(properties['latestReadyRevision'],'old-customer')
        self.assertEqual(properties['traffic'],[{'revisionName':'old-customer','percent':100}])
        self.assertNotIn('do-not-record',json.dumps(properties))
        self.assertIn('neon-jdbc-url',json.dumps(properties))

    def test_rabbit_port_required_only_for_workers_and_tls_only(self):
        for bad in [None,'5671',5672,True]:
            config=copy.deepcopy(self.config);config['rabbitPort']=bad
            validate_selected(config,'mock-bank-service');validate_selected(config,'gateway-service')
            for service in ['payment-service','notification-service']:
                with self.assertRaisesRegex(ValueError,'TLS port 5671'):validate_selected(config,service)

    def test_display_url_and_actual_url_are_recorded_in_evidence(self):
        config=copy.deepcopy(self.config);config['urls']={'customer-service':'https://display.run.app'}
        live=self.live()
        def fake(conf,*args):
            if args[2]=='describe':return copy.deepcopy(live)
            if args[2]=='get-iam-policy':return self.policy()
            if args[2]=='update-traffic':
                option=args[4]
                if option.startswith('--update-tags='):
                    tag,rev=option.split('=',2)[1:];live['status']['traffic'].append({'tag':tag,'revisionName':rev,'url':'https://tagged.run.app'})
                if option.startswith('--to-revisions='):
                    revision=option.split('=',1)[1].rsplit('=',1)[0];live['status']['traffic']=[{'revisionName':revision,'percent':100}]
            return {}
        with tempfile.TemporaryDirectory() as directory,patch('deploy_service.gcloud',side_effect=fake),patch('deploy_service.verify_runtime_permissions'),patch('deploy_service.health') as health:
            out=Path(directory)
            deploy(config,'customer-service',REGISTRY+'/customer-service@sha256:'+'a'*64,REGISTRY+'/collector@sha256:'+'b'*64,'gh-1',out)
            evidence=json.loads((out/'discovery.json').read_text());result=json.loads((out/'result.json').read_text())
            self.assertEqual(evidence['configuredUrls'],config['urls'])
            self.assertEqual(evidence['canonicalUrl'],'https://actual.run.app')
            self.assertTrue(evidence['warnings'])
            self.assertEqual(result['candidateUrl'],'https://tagged.run.app')
            self.assertEqual([c.args[1] for c in health.call_args_list],['https://tagged.run.app','https://actual.run.app'])
            self.assertEqual(health.call_args.args[0]['urls']['customer-service'],'https://actual.run.app')
