#!/usr/bin/env python3
"""Read recent existing service failures with bounded, sanitized diagnostic output."""
import json,re,subprocess
from pathlib import Path
from diagnose_revision import sanitize

def safe(text):
    text=sanitize(str(text))
    text=re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '[EMAIL]',text)
    text=re.sub(r'\bACC[A-Za-z0-9-]+\b','[ACCOUNT]',text)
    return text[:8000]
def main():
    services=['api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service']
    selector=' OR '.join('resource.labels.service_name="'+service+'"' for service in services)
    query='resource.type="cloud_run_revision" AND ('+selector+') AND (severity>=ERROR OR httpRequest.status>=500 OR jsonPayload.level="ERROR" OR jsonPayload.message=~"TRANSFER_.*(EXCEPTION|RECONCILIATION|NOT_COMPLETED)")'
    rows=json.loads(subprocess.check_output(['gcloud','logging','read',query,'--project=project-c9bd3d0e-266f-47bf-852','--freshness=72h','--order=desc','--limit=300','--format=json'],text=True))
    results=[]
    for row in rows:
        payload=row.get('jsonPayload',{});http=row.get('httpRequest',{})
        body=payload.get('body',{})
        if isinstance(body,str):
            try:body=json.loads(body)
            except ValueError:body={'message':body}
        if not isinstance(body,dict):body={}
        nested=body.get('message') or body.get('stringValue') or ''
        trace=row.get('trace','').split('/')[-1]
        results.append({'timestamp':row.get('timestamp'),'service':row.get('resource',{}).get('labels',{}).get('service_name'),
            'revision':row.get('resource',{}).get('labels',{}).get('revision_name'),
            'traceId':trace if re.fullmatch(r'[a-f0-9]{32}',trace) else '',
            'severity':row.get('severity'),'httpStatus':http.get('status'),'latency':http.get('latency'),
            'message':safe(payload.get('message') or nested or row.get('textPayload') or row.get('protoPayload',{}).get('status',{}).get('message','')),
            'stack':safe(payload.get('stack_trace') or body.get('stack_trace') or payload.get('attributes',{}).get('exception.stacktrace','')),
            'entryFields':sorted(row),'payloadFields':sorted(payload)})
    out=Path('artifacts/stored-errors');out.mkdir(parents=True,exist_ok=True)
    (out/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    print(json.dumps({'entries':len(results),'scope':'Last 72h; maximum 300 existing failure entries. No business requests or cloud mutations.'}))
if __name__=='__main__':main()
