"""Evaluate the actual cloud PromQL conditions with pinned promtool; no app outages."""
import argparse,json,subprocess,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'cloud'))
from otel_alerts import rules

def main():
    p=argparse.ArgumentParser();p.add_argument('--promtool',required=True);a=p.parse_args()
    allrules=rules();selected=allrules[:4];absent={k:v for k,v in allrules[7].items() if k!='requiresWarm'}
    with tempfile.TemporaryDirectory(prefix='otel-rule-tests-') as tmp:
        root=Path(tmp);rulefile=root/'rules.json'
        rulefile.write_text(json.dumps({'groups':[{'name':'cloud','rules':[*selected,absent]}]}))
        labels='job="payment-poc/api-gateway",http_route="payment-service",http_request_method="POST"'
        series=[
            {'series':'http_server_request_duration_seconds_count{'+labels+',http_response_status_code="200"}','values':'0+100x6 700+100x15'},
            {'series':'http_server_request_duration_seconds_count{'+labels+',http_response_status_code="500"}','values':'0+20x6 120+0x15'},
            {'series':'http_server_request_duration_seconds_bucket{'+labels+',le="1"}','values':'0+10x6 160+100x15'},
            {'series':'http_server_request_duration_seconds_bucket{'+labels+',le="5"}','values':'0+120x6 820+100x15'},
            {'series':'http_server_request_duration_seconds_bucket{'+labels+',le="+Inf"}','values':'0+120x6 820+100x15'},
            {'series':'poc_outbox_oldest_pending_seconds{job="payment-poc/payment-service"}','values':'90+0x6 0+0x15'},
            {'series':'poc_notification_oldest_unsent_seconds{job="payment-poc/notification-service"}','values':'90+0x6 0+0x15'},
        ]
        tests=[]
        for rule in selected:
            tests.extend([{'eval_time':'5m','alertname':rule['alert'],'exp_alerts':[{'exp_labels':{},'exp_annotations':{}}]},
                          {'eval_time':'15m','alertname':rule['alert'],'exp_alerts':[]}])
        absent_test={'interval':'1m','input_series':[{'series':'http_server_request_duration_seconds_count{job="payment-poc/auth-service"}','values':' '.join(['_']*12)+' 0+1x10'}],
                     'alert_rule_test':[{'eval_time':'11m','alertname':absent['alert'],'exp_alerts':[{'exp_labels':{'job':'payment-poc/auth-service'},'exp_annotations':{}}]},
                                        {'eval_time':'14m','alertname':absent['alert'],'exp_alerts':[]}]}
        case={'rule_files':[rulefile.as_posix()],'evaluation_interval':'30s','tests':[{'interval':'1m','input_series':series,'alert_rule_test':tests},absent_test]}
        file=root/'tests.json';file.write_text(json.dumps(case))
        subprocess.run([a.promtool,'test','rules',str(file)],check=True)
    print('PASS: actual error, latency, outbox and notification rules fire then recover')
if __name__=='__main__':main()
