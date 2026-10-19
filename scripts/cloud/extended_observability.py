#!/usr/bin/env python3
"""Manual sandbox verification of new wallet spans, RUM intake and cloud metrics."""
import argparse,json,subprocess,uuid
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlencode
from cloud_acceptance import request,eventually,validate_config

def main():
    p=argparse.ArgumentParser();p.add_argument('--project',default='project-c9bd3d0e-266f-47bf-852');a=p.parse_args()
    root=Path('artifacts/extended-observability');root.mkdir(parents=True,exist_ok=True);results=[]
    def discover(name):return subprocess.check_output(['gcloud','run','services','describe',name,'--project='+a.project,'--region=us-central1','--format=value(status.url)'],text=True).strip()
    frontend=discover('payment-frontend');validate_config({'project':a.project,'apiUrl':frontend})
    token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip();google={'Authorization':'Bearer '+token}
    def check(name,fn):
        try:fn();results.append({'check':name,'status':'PASS'})
        except Exception as error:results.append({'check':name,'status':'FAIL','reason':type(error).__name__});raise
        finally:(root/'results.json').write_text(json.dumps(results,indent=2))
    def rum():
        for name,value in [('PAGE_VIEW',1),('LCP',100),('CLS',.01),('JS_ERROR',1)]:
            request(frontend+'/api/v1/telemetry/rum',{'name':name,'value':value},{'Origin':frontend},(204,))
        request(frontend+'/api/v1/telemetry/rum',{'name':'LCP','value':1,'account':'must-not-be-accepted'},{'Origin':frontend},(400,))
        request(frontend+'/api/v1/telemetry/rum',{'name':'PAGE_VIEW','value':1},{'Origin':'https://untrusted.run.app'},(403,))
    check('Bounded RUM intake accepts metrics and rejects extra fields/origins',rum)
    suffix=uuid.uuid4().hex[:16];password=uuid.uuid4().hex+'Aa9!'
    user={'username':'otel_'+suffix,'password':password,'name':'OTel Verification','email':'otel_'+suffix+'@example.com'}
    request(frontend+'/api/v1/auth/register',user,expected=(200,201))
    login=request(frontend+'/api/v1/auth/login',{'username':user['username'],'password':password})
    headers={'Authorization':'Bearer '+login['accessToken']};trace=uuid.uuid4().hex
    def wallet():
        before=request(frontend+'/api/v1/wallet',headers={**headers,'traceparent':'00-'+trace+'-1234567890abcdef-01'})
        body={'idempotencyKey':str(uuid.uuid4()),'amount':.01}
        first=request(frontend+'/api/v1/wallet/deposits',body,headers,(200,201))
        replay=request(frontend+'/api/v1/wallet/deposits',body,headers,(200,201))
        assert first['id']==replay['id'],'Deposit replay returned another record'
        after=request(frontend+'/api/v1/wallet',headers=headers)
        assert Decimal(str(after['balance']))-Decimal(str(before['balance']))==Decimal('.01'),'Deposit replay changed money more than once'
        activity=request(frontend+'/api/v1/wallet/activity',headers=headers)
        assert sum(row['id']==first['id'] for row in activity)==1,'Deposit activity duplicated'
        request(frontend+'/api/v1/money-requests',headers=headers)
    check('Isolated INR 0.01 deposit replay changes balance and ledger exactly once',wallet)
    def span():
        data=request(f'https://cloudtrace.googleapis.com/v1/projects/{a.project}/traces/{trace}',headers=google)
        assert any(row.get('name')=='wallet.balance' for row in data.get('spans',[])),'Wallet operation span not stored'
        return True
    check('Wallet operation span stored under accepted request trace',lambda:eventually(span))
    families=['poc_wallet_operation_attempts_total','poc_wallet_operation_duration_seconds_count','poc_wallet_deposit_records',
              'poc_wallet_snapshot_healthy','poc_money_request_records','poc_money_request_snapshot_healthy',
              'poc_browser_events_total','poc_browser_measurement_count','otelcol_exporter_queue_size']
    def metrics():
        found=[]
        for metric in families:
            query='count('+metric+')'
            data=request(f'https://monitoring.googleapis.com/v1/projects/{a.project}/location/global/prometheus/api/v1/query?'+urlencode({'query':query}),headers=google)
            rows=data.get('data',{}).get('result',[])
            assert rows and float(rows[0]['value'][1])>0,'Missing '+metric
            found.append(metric)
        (root/'metrics-summary.json').write_text(json.dumps({'families':found,'traceId':trace},indent=2));return True
    check('Wallet browser and collector metric families stored',lambda:eventually(metrics))
    print(json.dumps({'status':'PASS','checks':len(results),'traceId':trace,'scope':'One isolated test user and INR 0.01 sandbox deposit; no tokens in evidence'}))
if __name__=='__main__':main()
