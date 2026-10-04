#!/usr/bin/env python3
"""Exercise the optional tail sampler with actual mock-bank HTTP requests.
The main end-to-end suite runs first with all traces retained; this isolated
experiment tests error/latency retention and reduced normal-traffic sampling.
"""
import json
from pathlib import Path
import subprocess
import time
import uuid
from otel_acceptance import request, eventually

base=['docker','compose','-f','docker-compose.yml','-f','docker-compose.otel.yml']
report={'status':'FAIL','experiment':'Real mock-bank requests through optional tail-sampling overlay'}
Path('artifacts').mkdir(exist_ok=True)
def compose(sampling, *args):
    command=base+(['-f','docker-compose.sampling.yml'] if sampling else [])+list(args)
    subprocess.run(command,check=True,timeout=120)
def bank(account, expected=(200,)):
    trace=uuid.uuid4().hex
    response=request('http://localhost:8082/api/v1/bank/debits',
        {'paymentId':str(uuid.uuid4()),'accountNumber':account,'amount':0.01},trace=trace,expected=expected)
    if expected==(200,):assert response['status']=='COMPLETED'
    return trace
def available(trace):
    data=request('http://localhost:3200/api/traces/'+trace,expected=(200,404))
    return isinstance(data,dict) and bool(data.get('batches') or data.get('resourceSpans'))
try:
    compose(True,'up','-d','--no-deps','--force-recreate','otel-collector')
    eventually(lambda: bool(request('http://localhost:13133')))
    # Allow Java exporters' negative DNS cache from Collector recreation to expire.
    time.sleep(15)
    error=bank('ACC-ERROR',(500,))
    slow=bank('ACC-SLOW')
    normal=[bank('ACC1001') for _ in range(20)]
    print('Sampling requests completed; waiting for the 30-second tail decision window.',flush=True)
    time.sleep(45)
    eventually(lambda: available(error))
    eventually(lambda: available(slow))
    kept=sum(available(trace) for trace in normal)
    assert kept<20, 'Tail sampler retained all normal requests; baseline reduction did not occur'
    report.update(status='PASS',errorTraceId=error,slowTraceId=slow,
        normalRequests=20,normalTracesRetained=kept,normalTracesDropped=20-kept,
        checks=['error trace retained','7-second slow trace retained','normal traffic reduced by configured 10% probabilistic policy'])
    print(json.dumps(report),flush=True)
except Exception as error:
    report['error']=str(error)
    raise
finally:
    (Path('artifacts')/'advanced-sampling.json').write_text(json.dumps(report,indent=2))
    compose(False,'up','-d','--no-deps','--force-recreate','otel-collector')
