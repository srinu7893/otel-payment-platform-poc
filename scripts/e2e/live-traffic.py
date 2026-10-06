#!/usr/bin/env python3
"""Small concurrent demo workload; not a load/capacity benchmark."""
import concurrent.futures
import json
from pathlib import Path
import statistics
import time
from otel_acceptance import login, payment, notification, eventually

token=login()
def submit(index):
    started=time.perf_counter()
    pid=payment(token)
    return {'paymentId':pid,'elapsedMs':round(1000*(time.perf_counter()-started),2),'status':'COMPLETED'}
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    rows=list(pool.map(submit,range(12)))
for row in rows:
    eventually(lambda:notification(token,row['paymentId']))
values=sorted(r['elapsedMs'] for r in rows)
result={'description':'12 real demo payments, concurrency 3; all notifications observed SENT; not a benchmark','requests':rows,'medianMs':statistics.median(values),'maxMs':max(values)}
Path('artifacts/traffic.json').write_text(json.dumps(result,indent=2))
print(json.dumps({k:v for k,v in result.items() if k!='requests'}))
