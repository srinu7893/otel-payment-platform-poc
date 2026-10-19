#!/usr/bin/env python3
"""Render Cloud Monitoring dashboards; publish only with explicit --apply. Never modifies IAM."""
import argparse,json,math,re,subprocess,time,urllib.request,urllib.error,urllib.parse
from pathlib import Path
from cloud_acceptance import google_failure

JAVA_SERVICES=['api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service']
def queries(service=None):
    job='job="payment-poc/'+service+'"' if service else 'job=~"payment-poc/.+"'
    selector='{'+job+',http_route=~".+",http_route!~".*actuator.*"}'
    count='http_server_request_duration_seconds_count'+selector
    errors=count[:-1]+',http_response_status_code=~"5.."}'
    group='job,http_route,http_request_method'
    denominator=f'sum by ({group}) (rate({count}[5m]))'
    numerator=f'sum by ({group}) (rate({errors}[5m]))'
    result={
        'Requests per second':f'sum by (job) (rate({count}[5m]))',
        'API operation traffic':denominator,
        'API operation request count - last hour':f'sum by ({group}) (increase({count}[1h]))',
        'API operation 5xx per second':f'{numerator} or (0 * {denominator})',
        'Technical availability by API (1 - 5xx ratio)':f'1 - (({numerator} or (0 * {denominator})) / {denominator})',
    }
    for percentile in [50,95,99]:
        result[f'API operation p{percentile} latency (seconds)']=f'histogram_quantile({percentile/100},sum by (le,{group}) (rate(http_server_request_duration_seconds_bucket{selector}[5m])))'
    result['JVM memory used (bytes)']='sum by (job) (jvm_memory_used_bytes{'+job+',jvm_memory_type=~".+"})'
    client_errors=f'sum by ({group}) (rate('+count[:-1]+',http_response_status_code=~"4.."}[5m]))'
    result['HTTP 4xx per second']=f'{client_errors} or (0 * {denominator})'
    return result

def sample_counts(data):
    """A NaN/Inf-only series is not evidence of a working panel."""
    if data.get('status')!='success':raise ValueError('PromQL query rejected')
    samples=[value for row in data.get('data',{}).get('result',[])
             for _,value in row.get('values',[]) ]
    return {'samples':len(samples),'finiteSamples':sum(math.isfinite(float(v)) for v in samples)}

def verify_queries(api,project,end=None):
    """Check the chart's last hour, rather than only the instant after traffic stops."""
    end=int(time.time() if end is None else end)
    base=f'https://monitoring.googleapis.com/v1/projects/{project}/location/global/prometheus/api/v1/query_range?'
    results=[]
    for service in JAVA_SERVICES:
        for title,query in queries(service).items():
            params={'query':query,'start':end-3600,'end':end,'step':60}
            counts=sample_counts(api(base+urllib.parse.urlencode(params)))
            results.append({'service':service,'panel':title,
                            'status':'DATA' if counts['finiteSamples'] else 'NO_DATA',
                            'windowSeconds':3600,**counts})
    return results

def dashboard(service=None):
    label=service or 'platform-overview'
    text={'title':'How to investigate','text':{'format':'MARKDOWN','content':
        'OTel application metrics from Managed Prometheus. Grouped by service job, route template and HTTP method. '
        'Health routes are excluded. Request counts are Prometheus estimates, not ledger audit counts. '
        'Availability is HTTP technical availability; business declines and pending outcomes need transaction investigation. '
        'No traffic produces an undefined ratio, not 100% availability. Histograms require observations. '
        'Open a request in Postman Visualize or Support > Observability to follow its Logs and Trace links. '
        'A missing series is not zero. Browser RUM is not enabled in the deployed frontend.'}}
    tiles=[{'xPos':0,'yPos':0,'width':12,'height':2,'widget':text}]
    for i,(title,query) in enumerate(queries(service).items()):
        tiles.append({'xPos':(i%2)*6,'yPos':2+(i//2)*4,'width':6,'height':4,'widget':{
            'title':title,'xyChart':{'dataSets':[{'timeSeriesQuery':{'prometheusQuery':query},'plotType':'LINE'}],
                'timeshiftDuration':'0s','yAxis':{'label':'','scale':'LINEAR'}}}})
    return {'displayName':'OTel Payment POC - '+label,'mosaicLayout':{'columns':12,'tiles':tiles}}

def render(directory):
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
    files=[]
    for service in [None,*JAVA_SERVICES]:
        path=directory/((service or 'platform-overview')+'.json')
        path.write_text(json.dumps(dashboard(service),indent=2)+'\n',encoding='utf-8');files.append(path)
    return files

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',required=True)
    p.add_argument('--out',default='deploy/cloud-run/dashboards');p.add_argument('--apply',action='store_true')
    p.add_argument('--verify-queries',action='store_true');args=p.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',args.project):raise ValueError('Invalid project')
    files=render(args.out)
    if not args.apply and not args.verify_queries:print('Rendered eight dashboards; no cloud changes');return 0
    token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
    def api(url,body=None,method=None):
        req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),method=method,
            headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=60) as response:return json.load(response)
    results=[];root=Path('artifacts/cloud-dashboards');root.mkdir(parents=True,exist_ok=True)
    try:
        if args.apply:
            base=f'https://monitoring.googleapis.com/v1/projects/{args.project}/dashboards'
            current=[];page=''
            while True:
                data=api(base+('?'+urllib.parse.urlencode({'pageToken':page}) if page else ''))
                current.extend(data.get('dashboards',[]));page=data.get('nextPageToken','')
                if not page:break
            for path in files:
                body=json.loads(path.read_text());matches=[d for d in current if d.get('displayName')==body['displayName']]
                if len(matches)>1:raise ValueError('Duplicate dashboard displayName; refusing ambiguous update')
                if matches:
                    body['name']=matches[0]['name'];body['etag']=api('https://monitoring.googleapis.com/v1/'+body['name'])['etag']
                    saved=api('https://monitoring.googleapis.com/v1/'+body['name'],body,'PATCH')
                else:saved=api(base,body,'POST')
                results.append({'dashboard':body['displayName'],'status':'PUBLISHED','name':saved['name'],
                    'url':'https://console.cloud.google.com/monitoring/dashboards/builder/'+saved['name'].split('/')[-1]+'?project='+args.project})
                (root/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
        if args.verify_queries:
            results.extend(verify_queries(api,args.project))
    except urllib.error.HTTPError as error:
        results.append(google_failure(error.code,error.read()))
        print(results[-1]);return 2 if error.code in (401,403) else 1
    finally:(root/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    print(json.dumps(results,indent=2))
    return 1 if any(row['status']=='NO_DATA' for row in results) else 0

if __name__=='__main__':raise SystemExit(main())
