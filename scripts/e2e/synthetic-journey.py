#!/usr/bin/env python3
"""On-demand isolated local fake-payment probe; never a production payment generator."""
import json
import time
import uuid
from pathlib import Path
from otel_acceptance import BASE,request,eventually,login,notification

stages=[];trace=uuid.uuid4().hex;started=time.monotonic();result={'status':'FAIL','traceId':trace,'stages':stages}
def step(name,fn):
    begin=time.monotonic();value=fn();stages.append({'stage':name,'seconds':round(time.monotonic()-begin,3),'status':'PASS'});return value

def run():
    token=step('login',login)
    step('invalid credentials rejected',lambda:request(BASE+'/api/v1/auth/login',{'username':'demo','password':'synthetic-wrong'},expected=(401,)))
    key='synthetic-'+uuid.uuid4().hex;body={'idempotencyKey':key,'accountNumber':'ACC1001','merchant':'Synthetic journey','amount':1}
    payment=step('payment completion',lambda:request(BASE+'/api/v1/payments',body,token,trace,(200,201)))
    assert payment['status']=='COMPLETED',payment['status'];result['paymentId']=payment['paymentId']
    replay=step('idempotent replay',lambda:request(BASE+'/api/v1/payments',body,token,trace,(200,201)))
    assert replay['paymentId']==payment['paymentId']
    step('notification delivered',lambda:eventually(lambda:notification(token,payment['paymentId']),timeout=120))
    result['status']='PASS'

try:
    run()
except Exception as error:
    # Never serialize HTTP response bodies/login tokens or exception messages.
    result['errorType']=type(error).__name__
    raise
finally:
    result['seconds']=round(time.monotonic()-started,3);Path('artifacts').mkdir(exist_ok=True)
    Path('artifacts/synthetic-journey.json').write_text(json.dumps(result,indent=2))
    now=str(time.time_ns())
    payload={'resourceMetrics':[{'resource':{'attributes':[{'key':'service.name','value':{'stringValue':'synthetic-monitor'}}]},'scopeMetrics':[{'scope':{'name':'poc.synthetic'},'metrics':[
      {'name':'poc.synthetic.journey.success','gauge':{'dataPoints':[{'timeUnixNano':now,'asDouble':1 if result['status']=='PASS' else 0}]}},
      {'name':'poc.synthetic.journey.duration','unit':'s','gauge':{'dataPoints':[{'timeUnixNano':now,'asDouble':result['seconds']}]}}
    ]}]}]}
    try:request('http://localhost:4318/v1/metrics',payload)
    except Exception as error:
        result['metricExportErrorType']=type(error).__name__;Path('artifacts/synthetic-journey.json').write_text(json.dumps(result,indent=2))
        if result['status']=='PASS':raise
    print(json.dumps(result),flush=True)
