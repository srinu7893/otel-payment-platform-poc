import unittest
from urllib.parse import urlparse,parse_qs
from otel_dashboards import dashboard,queries,JAVA_SERVICES,sample_counts,verify_queries

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

    def test_only_finite_samples_prove_a_panel_has_data(self):
        def response(values):return {'status':'success','data':{'result':[{'values':values}]}}
        self.assertEqual(sample_counts(response([[1,'NaN'],[2,'+Inf'],[3,'-Inf']]))['finiteSamples'],0)
        self.assertEqual(sample_counts(response([[1,'0'],[2,'0.25'],[3,'NaN']]))['finiteSamples'],2)
        with self.assertRaisesRegex(ValueError,'rejected'):sample_counts({'status':'error'})

    def test_verification_queries_history_and_does_not_hide_missing_latency(self):
        calls=[]
        def api(url):
            parsed=urlparse(url);params=parse_qs(parsed.query);calls.append((parsed,params))
            values=[] if 'histogram_quantile' in params['query'][0] else [[940,'0']]
            return {'status':'success','data':{'result':[{'values':values}]}}
        results=verify_queries(api,'test-project',end=1000)
        self.assertEqual(len(results),70)
        for parsed,params in calls:
            self.assertTrue(parsed.path.endswith('/query_range'))
            self.assertEqual(params['start'],['-2600']);self.assertEqual(params['end'],['1000'])
            self.assertEqual(params['step'],['60'])
        self.assertEqual(sum(r['status']=='NO_DATA' for r in results),21)

    def test_no_client_errors_are_zero_only_when_traffic_series_exists(self):
        query=queries('payment-service')['HTTP 4xx per second']
        self.assertIn('or (0 * sum by',query)
        self.assertNotIn('vector(0)',query)

if __name__=='__main__':unittest.main()
