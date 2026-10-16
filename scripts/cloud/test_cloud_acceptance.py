import copy,json,os,sys,tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
from cloud_acceptance import validate_config,validate_trace,validate_metrics,eventually,EvidenceBlocked,SERVICES,google_failure,main

class CloudTelemetryAcceptanceTest(unittest.TestCase):
    def trace(self):
        rows=[]
        for i,service in enumerate(sorted(SERVICES)):
            rows.append({'spanId':str(i+1),'parentSpanId':'99' if service=='api-gateway' else '1',
                'name':'outbox.publish' if service=='payment-service' else 'HTTP',
                'startTime':'2026-10-16T00:00:00Z','endTime':'2026-10-16T00:00:00.100Z',
                'labels':{'/service/name':service,**({'/db/system':'postgresql'} if service=='payment-service' else {})}})
        return {'traceId':'a'*32,'spans':rows}
    def test_partial_config_needs_no_unrelated_service_accounts_or_secrets(self):
        self.assertEqual(validate_config({'project':'valid-project','apiUrl':'https://gateway.run.app'}),('valid-project','https://gateway.run.app'))
    def test_placeholders_and_credential_urls_rejected(self):
        for origin in ['https://replace-api.run.app','https://user:pass@gateway.run.app','http://gateway.run.app','https://gateway.run.app/path']:
            with self.subTest(origin=origin),self.assertRaises(ValueError):validate_config({'project':'valid-project','apiUrl':origin})
    def test_connected_trace_has_safe_durations_and_all_services(self):
        result=validate_trace(self.trace(),'a'*32)
        self.assertEqual(set(result['services']),SERVICES)
        self.assertEqual(result['spans'][0]['durationMs'],100)
        self.assertNotIn('labels',result['spans'][0])
    def test_coincident_trace_id_with_disconnected_consumer_fails(self):
        data=self.trace()
        next(row for row in data['spans'] if row['labels']['/service/name']=='notification-service')['parentSpanId']='999'
        with self.assertRaisesRegex(AssertionError,'connected'):validate_trace(data,'a'*32)
    def test_missing_db_outbox_or_service_fails(self):
        for mutation in ['db','outbox','service']:
            data=self.trace()
            if mutation=='db':
                for row in data['spans']:row['labels'].pop('/db/system',None)
            if mutation=='outbox':
                for row in data['spans']:row['name']='HTTP'
            if mutation=='service':data['spans'].pop()
            with self.subTest(mutation=mutation),self.assertRaises(AssertionError):validate_trace(data,'a'*32)
    def test_managed_prometheus_job_mapping_requires_every_service(self):
        data={'status':'success','data':{'result':[{'metric':{'job':'payment-poc/'+service}} for service in SERVICES|{'auth-service'}]}}
        self.assertEqual(len(validate_metrics(data)['services']),7)
        data['data']['result'].pop()
        with self.assertRaises(AssertionError):validate_metrics(data)
    def test_permission_denial_is_not_polled_or_called_pass(self):
        with patch('cloud_acceptance.time.sleep') as sleep:
            with self.assertRaises(EvidenceBlocked):eventually(lambda:(_ for _ in ()).throw(EvidenceBlocked('denied')))
            sleep.assert_not_called()
    def test_cloud_denial_evidence_excludes_raw_error_payload(self):
        raw=json.dumps({'error':{'status':'PERMISSION_DENIED','message':'monitoring.timeSeries.list denied; token=SECRET',
            'details':[{'reason':'IAM_PERMISSION_DENIED','metadata':{'token':'SECRET'}}]}})
        result=google_failure(403,raw)
        self.assertEqual(result['permissions'],['monitoring.timeSeries.list'])
        self.assertEqual(result['reasons'],['IAM_PERMISSION_DENIED'])
        self.assertNotIn('SECRET',json.dumps(result))
    def test_failed_login_is_in_complete_evidence_without_money_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);config=root/'config.json'
            config.write_text(json.dumps({'project':'valid-project','apiUrl':'https://gateway.run.app'}))
            def respond(url,*args,**kwargs):
                if url.endswith('/auth/login'):raise AssertionError('HTTP 504')
                return {}
            with patch.dict(os.environ,{'ALLOW_FAKE_PAYMENT':'true'}),patch.object(sys,'argv',['cloud_acceptance.py','--config',str(config),'--out',str(root/'out')]),patch('cloud_acceptance.subprocess.check_output',return_value='https://gateway.run.app'),patch('cloud_acceptance.request',side_effect=respond) as call:
                self.assertEqual(main(),1)
            result=json.loads((root/'out/summary.json').read_text())
            self.assertEqual(result['status'],'FAIL')
            self.assertEqual(result['checks'][-1]['scenario'],'Test customer login')
            self.assertFalse(any('/api/v1/payments' in str(c) and len(c.args)>1 for c in call.call_args_list))

if __name__=='__main__':unittest.main()
