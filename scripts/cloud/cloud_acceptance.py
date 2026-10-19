#!/usr/bin/env python3
"""Manual sandbox business + stored telemetry acceptance. Never grants IAM or deploys services."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

PROJECT = 'project-c9bd3d0e-266f-47bf-852'
SERVICES = {'api-gateway','payment-service','customer-service','gateway-service','mock-bank-service','notification-service'}

class EvidenceBlocked(Exception):
    pass

def google_failure(status,raw):
    try:
        payload=json.loads(raw)
        error=payload.get('error',{}) if isinstance(payload,dict) else {}
    except (ValueError,TypeError):error={}
    if not isinstance(error,dict):error={}
    details=error.get('details',[])
    if not isinstance(details,list):details=[]
    details=[row for row in details if isinstance(row,dict)]
    return {'status':'BLOCKED' if status in (401,403) else 'FAIL','httpStatus':status,
        'apiStatus':error.get('status',''),
        'reasons':sorted({row['reason'] for row in details if row.get('reason')}),
        'permissions':sorted(set(re.findall(r'\b(?:monitoring|logging|cloudtrace)\.(?:dashboards|timeSeries|metricDescriptors|logEntries|entries|traces)\.[A-Za-z]+\b',error.get('message',''))))}

def request(url, body=None, headers=None, expected=(200,), response_headers=None):
    req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),
        headers={'Content-Type':'application/json',**(headers or {})})
    try:
        with urllib.request.urlopen(req,timeout=90) as response:
            status,raw=response.status,response.read()
            if response_headers is not None:response_headers.update({k.lower():v for k,v in response.headers.items()})
    except urllib.error.HTTPError as error:
        status,raw=error.code,error.read()
        if 'googleapis.com/' in url and status in (401,403):
            raise EvidenceBlocked(json.dumps(google_failure(status,raw))) from None
    if status not in expected:raise AssertionError('HTTP '+str(status)+'; expected '+str(expected))
    try:return json.loads(raw)
    except ValueError:return raw.decode()

def eventually(fn,seconds=180):
    deadline=time.monotonic()+seconds
    last='No stored evidence yet'
    while True:
        try:
            value=fn()
            if value:return value
        except EvidenceBlocked:raise
        except (AssertionError,urllib.error.URLError) as error:last=type(error).__name__
        if time.monotonic()>=deadline:raise AssertionError('Stored evidence did not converge: '+last)
        time.sleep(min(5,max(0,deadline-time.monotonic())))

def validate_config(config):
    project=config.get('project','')
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',project) or project.startswith('replace-'):
        raise ValueError('Valid project required')
    base=config.get('urls',{}).get('api-gateway') or config.get('apiUrl','')
    url=urllib.parse.urlparse(base)
    if url.scheme!='https' or not (url.hostname or '').endswith('.run.app') or url.netloc!=url.hostname or url.path or url.query or url.fragment or 'replace-' in base:
        raise ValueError('Canonical HTTPS api-gateway Cloud Run origin required')
    return project,base

def span_service(span):
    labels=span.get('labels',{})
    for key in ('/service/name','service.name','/service.name','service_name','g.co/r/cloud_run_revision/service_name','/g.co/r/cloud_run_revision/service_name'):
        if labels.get(key):return labels[key]
    return ''

def validate_trace(data,trace):
    if data.get('traceId')!=trace:raise AssertionError('Wrong stored trace')
    rows=data.get('spans',[])
    services={span_service(row) for row in rows}
    if not SERVICES<=services:raise AssertionError('Missing services: '+','.join(sorted(SERVICES-services)))
    if not any(row.get('name')=='outbox.publish' for row in rows):raise AssertionError('Missing durable outbox span')
    if not any(any('dbsystem' in re.sub(r'[^a-z]','',key.lower()) for key in row.get('labels',{})) for row in rows):
        raise AssertionError('Missing database instrumentation')
    children={}
    for row in rows:
        children.setdefault(str(row.get('parentSpanId','')),[]).append(row)
    gateways=[row for row in rows if span_service(row)=='api-gateway']
    def visit(row,connected):
        sid=str(row['spanId'])
        if sid in connected:return
        connected.add(sid)
        for child in children.get(sid,[]):visit(child,connected)
    # Cloud Run can insert a platform span above the gateway. The gateway need
    # not be a trace root, but one gateway's descendant tree must contain every
    # required service. Never union disconnected trees sharing a trace ID.
    complete=False
    for gateway in gateways:
        connected=set();visit(gateway,connected)
        if SERVICES<={span_service(row) for row in rows if str(row['spanId']) in connected}:
            complete=True;break
    if not complete:
        raise AssertionError('Service spans are not connected to the gateway request')
    # Only safe investigation metadata is written, never complete attributes or raw SQL/headers.
    summary=[]
    for row in rows:
        start=dt.datetime.fromisoformat(row['startTime'].replace('Z','+00:00'))
        end=dt.datetime.fromisoformat(row['endTime'].replace('Z','+00:00'))
        summary.append({'service':span_service(row),'spanId':str(row['spanId']),
            'parentSpanId':str(row.get('parentSpanId','')),'startTime':row['startTime'],
            'durationMs':round((end-start).total_seconds()*1000,3),
            'outboxPublish':row.get('name')=='outbox.publish'})
    return {'traceId':trace,'services':sorted(services-{''}),'spans':summary}

def metric_service(labels):
    return labels.get('service_name') or labels.get('job','').split('/')[-1]

def validate_metrics(data):
    if data.get('status')!='success':raise AssertionError('Metric query failed')
    rows=data.get('data',{}).get('result',[])
    observed={metric_service(row['metric']) for row in rows}
    required=SERVICES|{'auth-service'}
    if not required<=observed:raise AssertionError('Missing HTTP metrics: '+','.join(sorted(required-observed)))
    return {'services':sorted(observed), 'series':len(rows)}

def inspect_stored_trace(project,trace,root):
    """Recheck existing evidence without business traffic or a new payment."""
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',project) or not re.fullmatch(r'[0-9a-f]{32}',trace) or trace=='0'*32:
        raise ValueError('Valid project and nonzero lowercase trace ID required')
    root=Path(root);root.mkdir(parents=True,exist_ok=True)
    token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
    data=request(f'https://cloudtrace.googleapis.com/v1/projects/{project}/traces/{trace}',headers={'Authorization':'Bearer '+token})
    rows=data.get('spans',[])
    observed={span_service(row) for row in rows}-{''}
    result={'traceId':trace,'services':sorted(observed),'missingServices':sorted(SERVICES-observed),'spanCount':len(rows),
            'relationships':[{'service':span_service(row),'spanId':str(row['spanId']),
                              'parentSpanId':str(row.get('parentSpanId',''))} for row in rows]}
    try:
        summary=validate_trace(data,trace)
        (root/'trace-summary.json').write_text(json.dumps(summary,indent=2),encoding='utf-8')
        result['status']='PASS'
    except AssertionError as error:
        result.update(status='FAIL',reason=str(error))
    (root/'summary.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))
    return 0 if result['status']=='PASS' else 1

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config')
    parser.add_argument('--project',default=PROJECT)
    parser.add_argument('--region',default='us-central1')
    parser.add_argument('--out',default='artifacts/cloud-acceptance')
    parser.add_argument('--trace-id',help='Read-only inspection of an existing trace; no business requests')
    args=parser.parse_args()
    if args.trace_id:return inspect_stored_trace(args.project,args.trace_id,args.out)
    if args.config:config=json.loads(Path(args.config).read_text())
    else:
        if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',args.project) or not re.fullmatch(r'[a-z]+-[a-z]+[0-9]',args.region):raise ValueError('Invalid project or region')
        origin=subprocess.check_output(['gcloud','run','services','describe','api-gateway','--project='+args.project,'--region='+args.region,'--format=value(status.url)'],text=True).strip()
        config={'project':args.project,'apiUrl':origin}
    project,base=validate_config(config)
    if os.getenv('ALLOW_FAKE_PAYMENT')!='true':raise ValueError('ALLOW_FAKE_PAYMENT=true is required for the isolated sandbox')
    root=Path(args.out);root.mkdir(parents=True,exist_ok=True)
    results=[]
    def write(name,value):(root/name).write_text(json.dumps(value,indent=2),encoding='utf-8')
    def check(name,fn):
        started=time.monotonic()
        try:value=fn();state='PASS';reason=''
        except EvidenceBlocked as error:value=None;state='BLOCKED';reason=str(error)
        except Exception as error:value=None;state='FAIL';reason=type(error).__name__+(': '+str(error) if isinstance(error,AssertionError) else '')
        results.append({'scenario':name,'status':state,'seconds':round(time.monotonic()-started,2),**({'reason':reason} if reason else {})})
        write('results.json',results);print(state+': '+name+(' - '+reason if reason else ''),flush=True)
        return value
    def finish(trace=None,pid=None):
        status='FAIL' if any(row['status']=='FAIL' for row in results) else 'BLOCKED' if any(row['status']=='BLOCKED' for row in results) else 'PASS'
        write('summary.json',{'status':status,'checks':results,'traceId':trace,'paymentId':pid,'scope':'Stored signal evidence; no deployment, IAM change or deliberate outage'})
        return 0 if status=='PASS' else 2 if status=='BLOCKED' else 1
    check('Anonymous business API rejected',lambda:request(base+'/api/v1/payments',expected=(401,)))
    def private_services():
        for service in sorted((SERVICES|{'auth-service'})-{'api-gateway'}):
            origin=subprocess.check_output(['gcloud','run','services','describe',service,'--project='+project,'--region='+args.region,'--format=value(status.url)'],text=True).strip()
            validate_config({'project':project,'apiUrl':origin})
            request(origin+'/actuator/health',expected=(401,403))
    check('Private backend health rejects anonymous HTTP',private_services)
    login=check('Test customer login',lambda:request(base+'/api/v1/auth/login',{'username':os.getenv('TEST_CUSTOMER_USERNAME') or 'demo','password':os.getenv('TEST_CUSTOMER_PASSWORD') or 'demo123'}))
    if login is None:return finish()
    headers={'Authorization':'Bearer '+login['accessToken']}
    check('Customer cannot invoke support operations',lambda:request(base+'/api/v1/ops/health',headers=headers,expected=(403,)))
    wallet=check('Test wallet readable',lambda:eventually(lambda:request(base+'/api/v1/wallet',headers=headers)))
    if wallet is None:return finish()
    def funded():assert float(wallet['balance'])>=1,'Test wallet needs INR 1 of demo funds; no automatic deposit is performed'
    check('Test wallet funded',funded)
    if any(row['status']=='FAIL' for row in results):return finish()
    trace=uuid.uuid4().hex;key=str(uuid.uuid4());response_headers={}
    trace_headers={**headers,'traceparent':'00-'+trace+'-1234567890abcdef-01','X-Correlation-Id':key}
    body={'idempotencyKey':key,'accountNumber':wallet['accountNumber'],'merchant':'Isolated OTel acceptance','amount':1}
    write('transaction.json',{'traceId':trace,'correlationId':key,'idempotencyKey':key})
    payment=check('Sandbox payment accepted',lambda:request(base+'/api/v1/payments',body,trace_headers,(200,201),response_headers))
    if payment is None:return finish(trace)
    pid=payment['paymentId']
    write('transaction.json',{'paymentId':pid,'traceId':trace,'correlationId':key,'idempotencyKey':key})
    def completed():
        current=request(base+'/api/v1/payments/'+pid,headers=headers)
        assert current['status']=='COMPLETED','Payment not completed';return True
    check('Sandbox payment completed',lambda:eventually(completed))
    def response_trace():assert response_headers.get('x-trace-id')==trace,'Gateway did not report the active trace ID'
    check('Gateway reports the actual accepted request trace',response_trace)
    def notified():
        rows=request(base+'/api/v1/notifications?paymentId='+pid,headers=headers)['content']
        assert any(row.get('paymentId')==pid and row.get('status')=='SENT' for row in rows),'Notification not delivered'
        return True
    check('Async notification delivered',lambda:eventually(notified))
    def replay():
        current=request(base+'/api/v1/payments',body,headers,(200,201))
        assert current['paymentId']==pid and current['status']=='COMPLETED','Replay changed the payment'
    check('Idempotent replay returns same completed payment',replay)
    access=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
    google={'Authorization':'Bearer '+access}
    def logs():
        data=request('https://logging.googleapis.com/v2/entries:list',{'resourceNames':['projects/'+project],
            'filter':f'logName="projects/{project}/logs/payment-poc-otel" AND trace="projects/{project}/traces/{trace}"',
            'pageSize':100,'orderBy':'timestamp desc'},google)
        entries=data.get('entries',[])
        assert any('PAYMENT_COMPLETED' in json.dumps(row) for row in entries),'No stored correlated payment completion log'
        write('logs-summary.json',{'traceId':trace,'entries':len(entries),'paymentCompleted':True,
            'timestamps':[row.get('timestamp') for row in entries]});return True
    check('Cloud Logging contains this trace and payment outcome',lambda:eventually(logs))
    def spans():
        data=request(f'https://cloudtrace.googleapis.com/v1/projects/{project}/traces/{trace}',headers=google)
        write('trace-summary.json',validate_trace(data,trace));return True
    check('Connected HTTP DB outbox and notification spans stored',lambda:eventually(spans))
    def metrics():
        query='sum by (job,service_name) (http_server_request_duration_seconds_count)'
        url=f'https://monitoring.googleapis.com/v1/projects/{project}/location/global/prometheus/api/v1/query?'+urllib.parse.urlencode({'query':query})
        summary=validate_metrics(request(url,headers=google));write('metrics-summary.json',summary);return True
    check('Managed Prometheus contains all seven Java services',lambda:eventually(metrics))
    return finish(trace,pid)

if __name__=='__main__':raise SystemExit(main())
