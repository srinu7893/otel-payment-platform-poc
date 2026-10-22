import unittest
from poc_readiness import service_summary,alert_summary

class ReadinessTests(unittest.TestCase):
 def test_inventory_excludes_env_secrets_and_requires_ready(self):
  live={'spec':{'template':{'spec':{'containers':[{'name':'app','image':'repo@sha256:abc','env':[{'name':'JWT_SECRET','value':'must-not-leak'},{'name':'OTEL_RESOURCE_ATTRIBUTES','value':'service.namespace=poc,service.version=123abc'}]}]}}},'status':{'conditions':[{'type':'Ready','status':'True'}]}}
  result=service_summary('auth-service',live)
  self.assertTrue(result['ready']);self.assertEqual(result['otelServiceVersion'],'123abc');self.assertNotIn('must-not-leak',str(result))
  self.assertFalse(service_summary('auth-service',{})['ready'])
 def test_alert_inventory_is_not_delivery_proof(self):
  channel={'name':'projects/test/notificationChannels/1','type':'email','labels':{'email_address':'private@example.com'}}
  policy={'enabled':True,'notificationChannels':[channel['name']],'userLabels':{'managed_by':'otel-payment-poc'}}
  result=alert_summary([channel],[policy]);self.assertEqual(result['activation'],'CONFIGURED');self.assertNotIn('private@example.com',str(result))
  self.assertEqual(result['deliveryEvidence'],'NOT_VERIFIED_BY_INVENTORY')
  self.assertEqual(alert_summary([{**channel,'verificationStatus':'UNVERIFIED'}],[policy])['activation'],'PENDING')
  self.assertEqual(alert_summary([channel],[{**policy,'enabled':False}])['activation'],'PENDING')

if __name__=='__main__':unittest.main()
