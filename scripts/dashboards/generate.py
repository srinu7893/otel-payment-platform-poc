#!/usr/bin/env python3
"""Build service dashboards from metric families observed in passing run 14."""
import json
from urllib.parse import urlencode
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'observability/grafana/dashboards'
SERVICES=['api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service']
DB={'auth-service','customer-service','payment-service','mock-bank-service','notification-service'}
def panel(title,expr,unit='short',description='',kind='timeseries',source='prometheus'):
 return {'title':title,'type':kind,'description':description,'datasource':{'type':source,'uid':source},'targets':[{'refId':'A','expr':expr,'legendFormat':'{{service_name}} {{http_route}} {{status}} {{state}} {{pool_name}}'}],'fieldConfig':{'defaults':({'unit':unit,'min':0,'max':1} if unit=='percentunit' else {'unit':unit}),'overrides':[]}}
def write(uid,title,panels,service=None):
 for i,p in enumerate(panels):p.update(id=i+1,gridPos={'x':12*(i%2),'y':8*(i//2),'w':12,'h':8})
 obj={'uid':uid,'title':title,'schemaVersion':39,'version':1,'refresh':'10s','time':{'from':'now-15m','to':'now'},'tags':['otel','payment-poc','service' if service else 'overview'],'links':[{'title':'All dashboards','type':'dashboards','tags':['payment-poc'],'asDropdown':True,'includeVars':True,'keepTime':True},{'title':'Support runbook','type':'link','url':'https://github.com/srinu7893/otel-payment-platform-poc/blob/feature/otel-observability-manual-e2e/docs/DASHBOARDS_AND_DEPLOYMENT.md','targetBlank':True}],'panels':panels}
 if service:
  for label,query in [('Service traces','{ resource.service.name = "'+service+'" }'),('Error traces','{ resource.service.name = "'+service+'" && status = error }')]:
   panes={'trace':{'datasource':'tempo','queries':[{'refId':'A','datasource':{'type':'tempo','uid':'tempo'},'queryType':'traceql','query':query}],'range':{'from':'now-1h','to':'now'}}}
   obj['links'].append({'title':label,'type':'link','url':'/explore?'+urlencode({'schemaVersion':1,'panes':json.dumps(panes),'orgId':1}),'targetBlank':True})
 (OUT/(uid+'.json')).write_text(json.dumps(obj,indent=2)+'\n')
for service in SERVICES:
 s='{service_name="'+service+'"}'
 status='{service_name="'+service+'",http_response_status_code=~"5.."}'
 denominator='sum(rate(http_server_request_duration_seconds_count'+s+'[$__rate_interval]))'
 panels=[
 panel('HTTP requests per second',denominator,'reqps','Includes health probes. Route panels distinguish business traffic.'),
 panel('HTTP p95 latency','histogram_quantile(0.95,sum by (le) (rate(http_server_request_duration_seconds_bucket'+s+'[$__rate_interval])))','s'),
 panel('HTTP 5xx ratio','(sum(rate(http_server_request_duration_seconds_count'+status+'[$__rate_interval])) or vector(0)) / '+denominator,'percentunit','No traffic is undefined, not proven healthy. Business declines/unknown outcomes may return HTTP 200.'),
 panel('Request rate by route','sum by (http_route) (rate(http_server_request_duration_seconds_count'+s+'[$__rate_interval]))','reqps'),
 panel('JVM memory used','sum(jvm_memory_used_bytes{service_name="'+service+'",jvm_memory_type=~".+"})','bytes','OTel JVM pools only (jvm_memory_type), avoiding overlap with Micrometer area/id series; sum across instances, not container RSS.'),
 panel('JVM CPU utilization','max(jvm_cpu_recent_utilization_ratio'+s+')','percentunit','Maximum reported JVM utilization across instances.'),
 panel('JVM threads','sum(jvm_thread_count'+s+')','short'),
 panel('GC p95 pause','histogram_quantile(0.95,sum by (le) (rate(jvm_gc_duration_seconds_bucket'+s+'[$__rate_interval])))','s','Empty when there are no GC observations.'),
 panel('Reporting JVM instances','count(count by (service_instance_id) (jvm_thread_count'+s+'))','short','Metrics presence is not readiness. Exported/scraped samples can remain after a service stops.')]
 if service in DB:
  panels += [panel('Database pool connections by state','sum by (state) (db_client_connections_usage'+s+')'),panel('Database connection waiters','sum(db_client_connections_pending_requests'+s+')'),panel('Database acquire wait p95','histogram_quantile(0.95,sum by (le) (rate(db_client_connections_wait_time_milliseconds_bucket'+s+'[$__rate_interval]))) / 1000','s','Connection acquisition wait, not SQL execution duration. Inspect JDBC trace spans for queries.')]
 if service in {'payment-service','gateway-service','api-gateway'}:
  panels += [panel('Downstream HTTP p95','histogram_quantile(0.95,sum by (le) (rate(http_client_request_duration_seconds_bucket'+s+'[$__rate_interval])))','s','Aggregate client duration; use traces to identify the dependency.')]
 if service=='payment-service':panels += [panel('Durable payment rows by status','max by (status) (poc_payment_records'+s+')'),panel('Pending outbox events','max(poc_outbox_pending'+s+')'),panel('Oldest pending outbox age','max(poc_outbox_oldest_pending_seconds'+s+')','s')]
 if service=='notification-service':panels += [panel('Durable notification rows by status','max by (status) (poc_notification_records'+s+')'),panel('Oldest unsent record age','max(poc_notification_oldest_unsent_seconds'+s+')','s'),panel('Listener executions per second','sum(rate(spring_rabbitmq_listener_seconds_count'+s+'[$__rate_interval]))','ops','Listener executions may include retries, not unique delivered events.')]
 if service in {'payment-service','notification-service'}:panels += [panel('Business snapshot age','max(time()-(poc_business_snapshot_last_success_timestamp_seconds'+s+' > 0))','s','Only initialized timestamps are shown. An absent age is not healthy; inspect snapshot refresh health.'),panel('Business snapshot last refresh healthy','min(poc_business_snapshot_healthy'+s+')')]
 panels += [panel('Service raw logs','{service_name="'+service+'"}',description='Expand a record, copy trace_id and open Tempo in Explore. Increase the selected time window for async retries.',kind='logs',source='loki')]
 write('service-'+service,service+' — metrics and investigation',panels,service)
write('service-frontend','Frontend — journey visibility and coverage gaps',[
 panel('Frontend telemetry coverage','',kind='text',description='')
])
p=OUT/'service-frontend.json';o=json.loads(p.read_text());o['panels'][0].pop('targets');o['panels'][0].pop('datasource');o['panels'][0]['options']={'mode':'markdown','content':'## What is measured today\nThe synthetic monitor calls the API and does not measure the browser or Nginx. Backend edge metrics show API experience only. Browser functional tests exist. Browser RUM, frontend web-vitals and Nginx metrics are not instrumented yet; no frontend CPU/latency values are invented.'}
o['panels'] += [panel('Last on-demand synthetic journey result','poc_synthetic_journey_success',description='1 pass / 0 fail at recorded run time. No scheduled availability guarantee.'),panel('On-demand API journey duration','poc_synthetic_journey_duration_seconds','s','API login/payment/replay/delivery check, not browser load time.'),panel('API edge p95','histogram_quantile(0.95,sum by (le) (rate(http_server_request_duration_seconds_bucket{service_name="api-gateway"}[$__rate_interval])))','s','Backend edge latency; excludes browser rendering and static assets.')]
for i,panel_ in enumerate(o['panels']):panel_.update(id=i+1,gridPos={'x':12*(i%2),'y':8*(i//2),'w':12,'h':8})
p.write_text(json.dumps(o,indent=2)+'\n')
write('platform-overview','Platform — consolidated service comparison',[
 panel('Request rate by service','sum by (service_name) (rate(http_server_request_duration_seconds_count[$__rate_interval]))','reqps'),
 panel('p95 latency by service','histogram_quantile(0.95,sum by (le,service_name) (rate(http_server_request_duration_seconds_bucket[$__rate_interval])))','s'),
 panel('5xx ratio by service','(sum by (service_name) (rate(http_server_request_duration_seconds_count{http_response_status_code=~"5.."}[$__rate_interval])) or (0 * sum by (service_name) (rate(http_server_request_duration_seconds_count[$__rate_interval])))) / sum by (service_name) (rate(http_server_request_duration_seconds_count[$__rate_interval]))','percentunit','HTTP success is distinct from completed business work. No traffic yields undefined ratios.'),
 panel('JVM memory by service','sum by (service_name) (jvm_memory_used_bytes{jvm_memory_type=~".+"})','bytes'),
 panel('JVM CPU by service','max by (service_name) (jvm_cpu_recent_utilization_ratio)','percentunit'),
 panel('DB connection waiters by service','sum by (service_name) (db_client_connections_pending_requests)'),
 panel('Collector scrape status','up{job="otel-applications"}','short','This checks the shared exporter, not every application health endpoint.'),
 panel('Exporter queue utilization','otelcol_exporter_queue_size / clamp_min(otelcol_exporter_queue_capacity,1)','percentunit'),
 panel('Waiting outbox work','max(poc_outbox_pending)'),panel('Notification queue depth','rabbitmq_queue_messages_ready{queue="payments.notification"}'),
 panel('Business snapshot age by service','max by (service_name) (time()-(poc_business_snapshot_last_success_timestamp_seconds > 0))','s','Only initialized timestamps are shown. Inspect refresh health when a service has no successful snapshot.'),
 panel('Active warnings by name','sum by (alertname) (ALERTS{alertstate="firing"})','short','Empty means no firing series. Also check scrape/telemetry freshness.')])
print('Generated 7 backend dashboards, frontend coverage dashboard and consolidated overview')

# Native readiness is a separate signal from HTTP traffic or JVM metric presence.
for service in SERVICES+['frontend']:
 p=OUT/('service-'+service+'.json');o=json.loads(p.read_text());s='{service_name="'+service+'"}'
 additions=[
  panel('Readiness now (fresh probe)','poc_readiness_probe_success'+s+' and (time()-poc_readiness_probe_last_run_timestamp_seconds'+s+' < 30)','short','1 ready, 0 failed. Missing/stale is unknown. Direct probes every 10s while native monitor runs.',kind='stat'),
  panel('Observed readiness availability (selected window)','increase(poc_readiness_probe_successes_total'+s+'[$__range]) / increase(poc_readiness_probe_attempts_total'+s+'[$__range])','percentunit','Successful probe attempts / all completed attempts. Monitor outages are excluded, not successful; inspect monitor UP and freshness. Not a production SLA.',kind='stat'),
  panel('Probe age','time()-poc_readiness_probe_last_run_timestamp_seconds'+s,'s')]
 if service!='frontend':
  route='{service_name="'+service+'",http_route!~".*actuator.*",http_route=~".+"}'
  errors=route[:-1]+',http_response_status_code=~"[45].."}'
  counts='sum by (http_route,http_request_method) (increase(http_server_request_duration_seconds_count'+route+'[$__range]))'
  additions += [panel('API operation request count',counts,description='Estimated Prometheus counter increase in selected window, grouped by route template and method; excludes actuator. Not an audit count.',kind='table'),panel('API operation 4xx/5xx count','sum by (http_route,http_request_method) (increase(http_server_request_duration_seconds_count'+errors+'[$__range])) or (0 * '+counts+')',description='HTTP failures, including deliberate authentication/validation tests; business outcomes are separate.',kind='table')]
  for percentile in [50,95,99]:additions.append(panel('API operation p'+str(percentile)+' latency','histogram_quantile('+str(percentile/100)+',sum by (le,http_route,http_request_method) (rate(http_server_request_duration_seconds_bucket'+route+'[$__rate_interval])))','s',kind='table'))
  additions.append(panel('Business API traffic (health excluded)','sum(rate(http_server_request_duration_seconds_count'+route+'[$__rate_interval]))','reqps'))
 for panel_ in additions:
  if panel_['type'] in ('table','stat'):
   panel_['targets'][0]['instant']=True
   if panel_['type']=='table':panel_['targets'][0]['format']='table'
 o['panels']+=additions
 for i,panel_ in enumerate(o['panels']):panel_.update(id=i+1,gridPos={'x':12*(i%2),'y':8*(i//2),'w':12,'h':8})
 if service=='frontend':
  o['panels'][0]['options']['content']+='\nNative readiness checks the frontend HTTP endpoint. The scheduled fake-payment journey runs every five minutes; it still does not measure browser rendering.'
 p.write_text(json.dumps(o,indent=2)+'\n')
p=OUT/'platform-overview.json';o=json.loads(p.read_text());o['panels'] += [
 panel('Readiness by service (fresh probes)','poc_readiness_probe_success and (time()-poc_readiness_probe_last_run_timestamp_seconds < 30)'),
 panel('Observed readiness availability by service','increase(poc_readiness_probe_successes_total[$__range])/increase(poc_readiness_probe_attempts_total[$__range])','percentunit','Missing monitor intervals are unknown, not successful. Inspect monitor UP.'),
 panel('Readiness monitor UP','up{job="native-monitor"}'),
 panel('Scheduled fake-payment journey success','poc_scheduled_journey_success'),
 panel('Scheduled journey age','time()-poc_scheduled_journey_last_run_timestamp_seconds','s'),
 panel('Scheduled journey duration','poc_scheduled_journey_duration_seconds','s'),
 panel('Host available memory','poc_host_memory_available_bytes','bytes'),
 panel('Host CPU utilization','poc_host_cpu_utilization_ratio','percentunit'),
 panel('PostgreSQL database size','poc_database_size_bytes','bytes'),
 panel('PostgreSQL connections','poc_database_connections'),
 panel('PostgreSQL lock waiters','poc_database_lock_waiters'),
 panel('PostgreSQL rollback rate','rate(poc_database_rollbacks_total[$__rate_interval])','ops'),
 panel('PostgreSQL deadlocks','poc_database_deadlocks_total'),
 panel('PostgreSQL diagnostics healthy','poc_database_healthy'),
 panel('PostgreSQL diagnostics age','time()-poc_database_timestamp','s')]
for i,panel_ in enumerate(o['panels']):panel_.update(id=i+1,gridPos={'x':12*(i%2),'y':8*(i//2),'w':12,'h':8})
p.write_text(json.dumps(o,indent=2)+'\n')
write('domain-outcomes','Business operations — durable outcomes and delivery',[
 panel('Current durable rows by operation/status','max by (operation,status) (poc_domain_records)',description='Database state, not event counters. Includes payment, transfer and refund.'),
 panel('Completed creation-cohort ratio','max by (operation)(poc_domain_cohort_completed)/max by (operation)(poc_domain_cohort_records)','percentunit','Operations created between 60 and 2 minutes ago: current COMPLETED / all rows. Recent 2 minutes excluded for maturation; unresolved outcomes count against completion. Zero rows is unknown, not 100%.'),
 panel('Creation-cohort size','max by (operation)(poc_domain_cohort_records)'),
 panel('Event creation to notification SENT','max by (quantile)(poc_event_delivery_seconds)','s','SQL p50/p95/p99 over records SENT in the last hour, joined to durable source outbox events; includes publishing/queue/consumer delay. Start is event creation in the commit transaction, not an exact database commit timestamp. Per delivery record/channel.'),
 panel('Delivered sample count (last hour)','max(poc_event_delivery_records)'),
 panel('Domain snapshot healthy','min(poc_domain_snapshot_healthy)'),
 panel('Domain snapshot age','max(time()-(poc_domain_snapshot_last_success_timestamp_seconds > 0))','s')])
p=OUT/'service-frontend.json';o=json.loads(p.read_text());o['title']='Frontend — browser performance and readiness'
o['panels'][0]['options']['content']='## Local browser measurements\nThe development app reports web-vitals (FCP, LCP, INP, CLS, TTFB), page views and JavaScript error counts to the loopback native monitor through Vite. No URLs, DOM, user IDs, error messages or tokens are collected. LCP/CLS/INP may finalize after interaction or leaving the page; no observation means unknown. Values describe local test browsers, not production users. Production builds do not enable this receiver.'
o['panels'] += [panel('Latest browser timing observations','poc_browser_last_value{metric=~"FCP|LCP|INP|TTFB"}','ms','Latest observation per metric; not a population percentile.'),panel('Latest layout shift score','poc_browser_last_value{metric="CLS"}'),panel('Browser page views','poc_browser_observation_sum{metric="PAGE_VIEW"}'),panel('Browser JavaScript errors','poc_browser_observation_sum{metric="JS_ERROR"}','short','Count only; error text is not exported.'),panel('Browser observation age','time()-poc_browser_last_observation_timestamp_seconds','s')]
for i,panel_ in enumerate(o['panels']):panel_.update(id=i+1,gridPos={'x':12*(i%2),'y':8*(i//2),'w':12,'h':8})
p.write_text(json.dumps(o,indent=2)+'\n')
