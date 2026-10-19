#!/usr/bin/env python3
"""Render or manually publish bounded POC alerts. Never grants IAM or creates channels."""
import argparse,json,re,subprocess,urllib.request
from pathlib import Path
from otel_dashboards import JAVA_SERVICES

def rules():
    job='job="payment-poc/api-gateway",http_route=~".+",http_route!~".*actuator.*|.*telemetry.*"'
    requests='http_server_request_duration_seconds_count{'+job+'}'
    total=f'sum(rate({requests}[5m]))'
    errors='sum(rate(http_server_request_duration_seconds_count{'+job+',http_response_status_code=~"5.."}[5m]))'
    latency='histogram_quantile(0.95,sum by(le)(rate(http_server_request_duration_seconds_bucket{'+job+'}[5m])))'
    return [
        {'alert':'PaymentApiErrors','expr':f'({errors}/{total} > 0.05) and ({total} > 0.1)','for':'120s'},
        {'alert':'PaymentApiLatency','expr':f'({latency} > 2.5) and ({total} > 0.1)','for':'120s'},
        {'alert':'PaymentOutboxDelayed','expr':'max(poc_outbox_oldest_pending_seconds{job="payment-poc/payment-service"}) > 60','for':'60s'},
        {'alert':'NotificationDeliveryDelayed','expr':'max(poc_notification_oldest_unsent_seconds{job="payment-poc/notification-service"}) > 60','for':'60s'},
        {'alert':'BusinessSnapshotStale','expr':'(time() - max(poc_business_snapshot_last_success_timestamp_seconds{job="payment-poc/payment-service"}) > 60) or (min(poc_business_snapshot_healthy{job="payment-poc/payment-service"}) == 0)','for':'60s'},
        {'alert':'CollectorQueuePressure','expr':'max(otelcol_exporter_queue_size{job="payment-poc/otelcol-cloud-run"}/otelcol_exporter_queue_capacity{job="payment-poc/otelcol-cloud-run"}) > 0.8','for':'60s'},
        *[{'alert':'TelemetryAbsent_'+service.replace('-','_'),'expr':f'absent_over_time(http_server_request_duration_seconds_count{{job="payment-poc/{service}"}}[10m])','for':'60s','requiresWarm':True} for service in JAVA_SERVICES],
    ]

def policies(channels=(),warm=False):
    result=[]
    for rule in rules():
        result.append({'displayName':'OTel Payment POC - '+rule['alert'],'combiner':'OR',
            'enabled':not rule.get('requiresWarm',False) or warm,
            'notificationChannels':list(channels),'userLabels':{'owner':'poc-support','managed_by':'otel-payment-poc'},
            'documentation':{'mimeType':'text/markdown','content':
                'Inspect service/API dashboard, correlated logs and trace waterfall. Check snapshot freshness before interpreting business counts. '
                'No-data alerts require always-on expected emitters and are disabled by default for scale-to-zero services. '
                'Do not retry money movement without its original idempotency key. '
                'Runbook: https://github.com/srinu7893/otel-payment-platform-poc/blob/main/docs/OTEL_REMAINING_FEATURES.md'},
            'conditions':[{'displayName':rule['alert'],'conditionPrometheusQueryLanguage':{
                'query':rule['expr'],'duration':rule['for'],'evaluationInterval':'30s',
                'ruleGroup':'otel-payment-poc','alertRule':rule['alert']}}]})
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',required=True)
    p.add_argument('--channel',action='append',default=[]);p.add_argument('--apply',action='store_true')
    p.add_argument('--warm-expected',action='store_true');p.add_argument('--out',default='deploy/cloud-run/alerts');a=p.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',a.project):raise ValueError('Invalid project')
    if any(not re.fullmatch('projects/'+re.escape(a.project)+r'/notificationChannels/[0-9]+',c) for c in a.channel):raise ValueError('Use an existing channel resource in this project')
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    rendered=policies(a.channel,a.warm_expected)
    for item in rendered:(out/(item['displayName'].split(' - ')[-1]+'.json')).write_text(json.dumps(item,indent=2)+'\n')
    (out/'prometheus-rules.json').write_text(json.dumps({'groups':[{'name':'otel-cloud','rules':[{k:v for k,v in rule.items() if k!='requiresWarm'} for rule in rules()]}]},indent=2)+'\n')
    if not a.apply:print('Rendered policies only; no cloud changes');return
    if not a.channel:raise ValueError('Select an existing notification channel before publication')
    token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
    def api(url,body=None,method=None):
        req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),method=method,
            headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=30) as response:return json.load(response)
    base='https://monitoring.googleapis.com/v3/projects/'+a.project
    for channel in a.channel:
        live=api('https://monitoring.googleapis.com/v3/'+channel)
        if not live.get('enabled',True) or live.get('verificationStatus')=='UNVERIFIED':raise ValueError('Channel is disabled or unverified')
    current=[];page=''
    from urllib.parse import urlencode
    while True:
        data=api(base+'/alertPolicies'+('?'+urlencode({'pageToken':page}) if page else ''))
        current.extend(data.get('alertPolicies',[]));page=data.get('nextPageToken','')
        if not page:break
    results=[]
    for item in rendered:
        matches=[row for row in current if row.get('displayName')==item['displayName']]
        if len(matches)>1:raise ValueError('Duplicate policy name')
        if matches:
            if matches[0].get('userLabels',{}).get('managed_by')!='otel-payment-poc':raise ValueError('Refusing to replace an unmanaged policy')
            item['name']=matches[0]['name'];saved=api('https://monitoring.googleapis.com/v3/'+item['name'],item,'PATCH')
        else:saved=api(base+'/alertPolicies',item,'POST')
        results.append({'name':saved['name'],'displayName':saved['displayName'],'enabled':saved.get('enabled')})
    evidence=Path('artifacts/cloud-alerts');evidence.mkdir(parents=True,exist_ok=True)
    (evidence/'results.json').write_text(json.dumps(results,indent=2));print('Published '+str(len(results))+' policies; real notification delivery requires a firing/recovery test')
if __name__=='__main__':main()
