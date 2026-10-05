#!/usr/bin/env python3
"""Validate Grafana provisioning and evaluate every generated dashboard query."""
import base64,json,math,os,time,urllib.parse,urllib.request
from pathlib import Path
from otel_acceptance import request,eventually
ROOT=Path('observability/grafana/dashboards');results=[]
auth='Basic '+base64.b64encode(('admin:'+os.environ.get('GRAFANA_ADMIN_PASSWORD','otel-demo-admin')).encode()).decode()
def grafana(path):
 with urllib.request.urlopen(urllib.request.Request('http://localhost:3001'+path,headers={'Authorization':auth}),timeout=20) as r:return json.load(r)
def metric(expr):
 data=request('http://localhost:9090/api/v1/query?'+urllib.parse.urlencode({'query':expr}));assert data['status']=='success';return data['data']['result']
for p in sorted(ROOT.glob('*.json')):
 d=json.loads(p.read_text());live=grafana('/api/dashboards/uid/'+d['uid'])['dashboard'];assert len(live['panels'])==len(d['panels'])
 for panel in d['panels']:
  if panel.get('datasource',{}).get('uid')!='prometheus':continue
  for target in panel.get('targets',[]):
   expr=target['expr'].replace('$__rate_interval','5m').replace('$service','.+')
   rows=metric(expr);results.append({'dashboard':d['uid'],'panel':panel['title'],'status':'PASS','series':len(rows),'note':'Query accepted; empty/NaN can mean no observations or no traffic, not health'})
for name in ['api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service']:
 def required():
  for family in ['http_server_request_duration_seconds_count','jvm_memory_used_bytes','jvm_cpu_recent_utilization_ratio','jvm_thread_count']:
   rows=metric(f'{family}{{service_name="{name}"}}');assert rows and any(math.isfinite(float(r['value'][1])) for r in rows),name+' missing '+family
  return True
 eventually(required,timeout=90)
 assert metric(f'jvm_memory_used_bytes{{service_name="{name}",jvm_memory_type=~".+"}}'),name+' missing OTel memory pool labels'
 results.append({'dashboard':'service-'+name,'status':'PASS','requiredMetrics':'HTTP, memory, CPU and threads present'})
Path('artifacts/dashboard-review.json').write_text(json.dumps(results,indent=2));print('PASS:',len(results),'query/provisioning/coverage checks')
# Retain resource/label mappings for the cloud migration review.
Path('artifacts/dashboard-metric-labels.json').write_text(json.dumps({n:metric(n) for n in ['jvm_memory_used_bytes','db_client_connections_usage','jvm_thread_count']},indent=2))
