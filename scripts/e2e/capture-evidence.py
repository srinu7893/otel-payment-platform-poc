#!/usr/bin/env python3
"""Best-effort diagnostics, even when acceptance fails; never changes PASS/FAIL."""
import json, os, subprocess, urllib.request, urllib.parse
from pathlib import Path
root=Path('artifacts');root.mkdir(exist_ok=True)
urls={
 'grafana-frontend-settings':'http://localhost:3001/api/frontend/settings',
 'prometheus-targets':'http://localhost:9090/api/v1/targets',
 'prometheus-alerts':'http://localhost:9090/api/v1/alerts',
 'prometheus-series':'http://localhost:9090/api/v1/label/__name__/values',
 'http-metrics':'http://localhost:9090/api/v1/query?'+urllib.parse.urlencode({'query':'http_server_request_duration_seconds_count'}),
 'outbox-metrics':'http://localhost:9090/api/v1/query?'+urllib.parse.urlencode({'query':'poc_outbox_publish_attempts_total'}),
 'service-graph':'http://localhost:9090/api/v1/query?'+urllib.parse.urlencode({'query':'traces_service_graph_request_total'}),
 'recent-payment-logs':'http://localhost:3100/loki/api/v1/query_range?'+urllib.parse.urlencode({'query':'{service_name="payment-service"}','limit':200}),
}
for name,url in urls.items():
 try:
  with urllib.request.urlopen(url,timeout=15) as response:data=json.load(response)
  (root/(name+'.json')).write_text(json.dumps(data,indent=2))
 except Exception as error:(root/(name+'-error.txt')).write_text(str(error))
queries={
 'database-status':"SELECT 'payments' AS kind,status,count(*) FROM payment.payments GROUP BY status UNION ALL SELECT 'transfers',status,count(*) FROM payment.transfers GROUP BY status UNION ALL SELECT 'refunds',status,count(*) FROM payment.refunds GROUP BY status UNION ALL SELECT 'outbox',status,count(*) FROM payment.outbox_event GROUP BY status;",
 'outbox-context':"SELECT status,count(*) AS events,count(traceparent) AS traced_events,sum(attempts) AS failed_attempts FROM payment.outbox_event GROUP BY status;"
}
for name,query in queries.items():
 try:
  result=subprocess.run(['bash','scripts/otel-compose.sh','exec','-T','postgres','psql','-U',os.environ.get('POSTGRES_USER','payments'),'-d',os.environ.get('POSTGRES_DB','payments'),'-c',query],capture_output=True,text=True,timeout=30)
  (root/(name+'.txt')).write_text(result.stdout+result.stderr)
 except Exception as error:(root/(name+'-error.txt')).write_text(str(error))
