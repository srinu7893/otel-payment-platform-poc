import unittest
from otel_alerts import rules,policies

class AlertPolicyTest(unittest.TestCase):
    def test_missing_data_stays_disabled_for_scale_to_zero(self):
        rendered=policies(['projects/test-project/notificationChannels/123'])
        absent=[p for p in rendered if 'TelemetryAbsent' in p['displayName']]
        self.assertEqual(len(absent),7)
        self.assertTrue(all(not p['enabled'] for p in absent))
        self.assertTrue(all(p['enabled'] for p in policies(warm=True)))
    def test_error_and_latency_need_observed_traffic(self):
        selected=rules()[:2]
        for rule in selected:
            self.assertIn('> 0.1',rule['expr']);self.assertIn('payment-poc/api-gateway',rule['expr'])
            self.assertIn('telemetry',rule['expr']);self.assertNotIn('vector(0)',rule['expr'])
    def test_policy_names_channels_and_ownership_are_bounded(self):
        for policy in policies(['projects/test-project/notificationChannels/123']):
            self.assertEqual(policy['notificationChannels'],['projects/test-project/notificationChannels/123'])
            self.assertEqual(policy['userLabels']['managed_by'],'otel-payment-poc')
            self.assertEqual(policy['conditions'][0]['conditionPrometheusQueryLanguage']['evaluationInterval'],'30s')

if __name__=='__main__':unittest.main()
