#!/usr/bin/env python3
"""Explicitly invoked, isolated fake-payment cloud checks; never run in ordinary CI."""
import argparse,json,os,subprocess,time,urllib.request,urllib.error,urllib.parse,uuid
from pathlib import Path
from run_release import validate,SERVICES,health

def request(url,body=None,headers=None,expected=(200,)):
    req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),
        headers={'Content-Type':'application/json',**(headers or {})})
    try:
        with urllib.request.urlopen(req,timeout=15) as response:status,raw=response.status,response.read()
    except urllib.error.HTTPError as error:status,raw=error.code,error.read()
    assert status in expected, f'HTTP {status}; expected {expected}'
    try:return json.loads(raw)
    except ValueError:return raw.decode()

def eventually(fn,seconds=240):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        try:
            value=fn()
            if value:return value
        except (AssertionError,urllib.error.HTTPError):pass
        time.sleep(5)
    raise AssertionError('Cloud evidence did not reach expected state within the polling window')

def main():
    p=argparse.ArgumentParser();p.add_argument('--config',required=True);args=p.parse_args()
    config=validate(json.loads(Path(args.config).read_text()))
    if os.getenv('ALLOW_FAKE_PAYMENT')!='true':raise ValueError('Only enable ALLOW_FAKE_PAYMENT for this isolated PoC')
    username,password,account=[os.environ[key] for key in ['TEST_CUSTOMER_USERNAME','TEST_CUSTOMER_PASSWORD','TEST_ACCOUNT_NUMBER']]
    root=Path('artifacts/cloud-acceptance');root.mkdir(parents=True,exist_ok=True)
    results=[]
    def check(name,fn):
        started=time.monotonic()
        try:fn();results.append({'scenario':name,'status':'PASS','seconds':round(time.monotonic()-started,2)})
        except Exception as error:results.append({'scenario':name,'status':'FAIL','errorType':type(error).__name__});raise
        finally:(root/'results.json').write_text(json.dumps(results,indent=2))
    base=config['urls']['api-gateway']
    check('All eight cloud services ready',lambda:[health(config,config['urls'][s],s) for s in SERVICES])
    check('Private backend rejects anonymous HTTP',lambda:[request(config['urls'][s]+'/actuator/health',expected=(401,403)) for s in SERVICES if s not in {'frontend','api-gateway'}])
    check('Application API rejects missing customer token',lambda:request(base+'/api/v1/payments',expected=(401,)))
    customer=request(base+'/api/v1/auth/login',{'username':username,'password':password})['accessToken']
    headers={'Authorization':'Bearer '+customer}
    check('Customer cannot invoke support operations',lambda:request(base+'/api/v1/ops/health',headers=headers,expected=(403,)))
    trace=uuid.uuid4().hex;key='cloud-acceptance-'+uuid.uuid4().hex
    trace_headers={**headers,'traceparent':'00-'+trace+'-1234567890abcdef-01','X-Correlation-Id':key}
    payment_body={'idempotencyKey':key,'accountNumber':account,'merchant':'Isolated OTel Cloud Acceptance','amount':1.00}
    result=request(base+'/api/v1/payments',payment_body,trace_headers,(200,201));pid=result['paymentId']
    check('Simulated payment completes',lambda:assert_completed(result))
    def notified():
        data=request(base+'/api/v1/notifications?paymentId='+pid,headers=headers)
        return any(row.get('paymentId')==pid and row.get('status')=='SENT' for row in data['content'])
    check('Asynchronous notification delivered',lambda:eventually(notified))
    def replay():
        repeated=request(base+'/api/v1/payments',payment_body,headers,(200,201))
        assert repeated['paymentId']==pid and repeated['status']=='COMPLETED'
    check('Idempotent replay returns same completed payment',replay)
    access=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
    google={'Authorization':'Bearer '+access};project=config['project']
    def logs():
        data=request('https://logging.googleapis.com/v2/entries:list',{
            'resourceNames':['projects/'+project],
            'filter':'logName="projects/'+project+'/logs/payment-poc-otel" AND trace="projects/'+project+'/traces/'+trace+'"',
            'pageSize':100,'orderBy':'timestamp desc'},google)
        entries=data.get('entries',[])
        assert entries, 'No OTLP-exported logs with this trace ID'
        assert any('PAYMENT_COMPLETED' in json.dumps(row) for row in entries), 'Missing correlated business outcome'
        (root/'correlated-logs.json').write_text(json.dumps(data,indent=2));return True
    check('Cloud Logging stores this payment trace and outcome',lambda:eventually(logs))
    def spans():
        data=request('https://cloudtrace.googleapis.com/v1/projects/'+project+'/traces/'+trace,headers=google)
        rows=data.get('spans',[])
        assert len(rows)>10 and any(row.get('name')=='outbox.publish' for row in rows), 'Missing HTTP/DB/async trace spans'
        (root/'trace.json').write_text(json.dumps(data,indent=2));return True
    check('Cloud Trace stores distributed spans and durable outbox span',lambda:eventually(spans))
    def metrics():
        query='http_server_request_duration_seconds_count'
        url='https://monitoring.googleapis.com/v1/projects/'+project+'/location/global/prometheus/api/v1/query?'+urllib.parse.urlencode({'query':query})
        data=request(url,headers=google);assert data.get('status')=='success' and data['data']['result'], 'Missing application HTTP series'
        (root/'http-metrics.json').write_text(json.dumps(data,indent=2));return True
    check('Managed Prometheus stores application HTTP metrics',lambda:eventually(metrics))
    (root/'transaction.json').write_text(json.dumps({'paymentId':pid,'traceId':trace,'idempotencyKey':key,'status':result['status']},indent=2))
    print('PASS: cloud acceptance; evidence contains fake transaction IDs and signal responses, no login tokens')

def assert_completed(result):assert result['status']=='COMPLETED', 'Payment not completed'
if __name__=='__main__':main()
