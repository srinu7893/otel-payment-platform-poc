import unittest
from run_release import render,CALLS
import test_deploy_service

class RegistrationContractTest(unittest.TestCase):
    def test_auth_uses_only_private_customer_and_bank_origins(self):
        fixture=test_deploy_service.IndependentDeploymentTest();fixture.setUp()
        try:
            c=fixture.config
            c['serviceAccounts']['auth-service']=next(iter(c['serviceAccounts'].values()))
            manifest=render(c,'auth-service',fixture.image,fixture.collector,'test-1')
            env={e['name']:e.get('value') for e in manifest['spec']['template']['spec']['containers'][0]['env']}
            self.assertEqual(CALLS['auth-service'],['customer-service','mock-bank-service'])
            self.assertEqual(env['CLIENTS_CUSTOMER_BASE_URL'],c['urls']['customer-service'])
            self.assertEqual(env['CLIENTS_BANK_BASE_URL'],c['urls']['mock-bank-service'])
            self.assertEqual(set(env['CLOUD_RUN_AUDIENCES'].split(',')),{c['urls'][s] for s in CALLS['auth-service']})
        finally:fixture.doCleanups()
