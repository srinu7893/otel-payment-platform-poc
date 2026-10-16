import unittest
from otel_dashboards import dashboard,queries,JAVA_SERVICES

class OtelDashboardsTest(unittest.TestCase):
    def test_every_service_has_route_and_method_latency_and_availability_panels(self):
        for service in JAVA_SERVICES:
            content=queries(service)
            self.assertEqual(len(content),10)
            for name in ['API operation traffic','API operation p95 latency (seconds)','Technical availability by API (1 - 5xx ratio)']:
                self.assertIn('job="payment-poc/'+service+'"',content[name])
                self.assertIn('http_route',content[name]);self.assertIn('http_request_method',content[name])
            availability=content['Technical availability by API (1 - 5xx ratio)']
            self.assertNotIn('clamp_min',availability)
            self.assertIn('or (0 *',availability)
    def test_panels_fit_layout_and_warn_about_missing_data(self):
        data=dashboard()
        self.assertEqual(len(data['mosaicLayout']['tiles']),11)
        self.assertIn('No traffic',data['mosaicLayout']['tiles'][0]['widget']['text']['content'])
        for tile in data['mosaicLayout']['tiles']:self.assertLessEqual(tile['xPos']+tile['width'],12)

if __name__=='__main__':unittest.main()
