import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from deploy_service import PROJECT, REGISTRY, service_name, selected_manifest, deploy
from run_release import SERVICES

class IndependentDeploymentTest(unittest.TestCase):
    def setUp(self):
        self.config=json.loads((Path(__file__).resolve().parents[2]/'deploy/cloud-run/service-config.example.json').read_text())
        self.config['network']='poc-network';self.config['subnetwork']='poc-subnet';self.config['rabbitHost']='broker.internal'
        self.config['urls']={s:'https://'+s+'.run.app' for s in SERVICES}
        self.collector=REGISTRY+'/collector@sha256:'+'b'*64
        self.image=REGISTRY+'/auth-service@sha256:'+'a'*64

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
        calls=[];state={'status':{'url':self.config['urls']['auth-service'],'traffic':[{'revisionName':'auth-old','percent':100}]}}
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
