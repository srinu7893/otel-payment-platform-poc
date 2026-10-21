"""Collect bounded, sanitized stored logs for the actual tested request traces."""
import argparse,json,re,subprocess,time
from pathlib import Path
from cloud_acceptance import request
from diagnose_revision import sanitize

def clean(value):
 text=sanitize(str(value))
 text=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED JWT]',text)
 text=re.sub(r'''(?i)(["']?(?:password|access_?token|refresh_?token|api_key)["']?\s*[:=]\s*)("[^"]*"|'[^']*'|[^ ,;]+)''',r'\1[REDACTED]',text)
 return text[:2000]
def main():
 p=argparse.ArgumentParser();p.add_argument('--report',default='artifacts/api-tests/latest/results.json');p.add_argument('--project',default='project-c9bd3d0e-266f-47bf-852');a=p.parse_args()
 if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',a.project):raise ValueError('Invalid project')
 report_path=Path(a.report);report=json.loads(report_path.read_text());out=report_path.parent
 tested=[row for suite in report['suites'] for row in suite['requests'] if re.fullmatch(r'[a-f0-9]{32}',row.get('traceId',''))]
 traces=sorted({r['traceId'] for r in tested});token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
 if not traces:raise ValueError('No accepted trace IDs in execution evidence')
 clauses=' OR '.join('trace="projects/'+a.project+'/traces/'+trace+'"' for trace in traces)
 for attempt in range(3):
  try:
   data=request('https://logging.googleapis.com/v2/entries:list',{'resourceNames':['projects/'+a.project],'filter':'('+clauses+')','orderBy':'timestamp desc','pageSize':1000},headers={'Authorization':'Bearer '+token})
   observed={entry.get('trace','').split('/')[-1] for entry in data.get('entries',[])}
   if set(traces)<=observed:break
  except AssertionError:
   if attempt==2:raise
  if attempt<2:time.sleep(10)
 rows=[]
 for entry in data.get('entries',[]):
  payload=entry.get('jsonPayload',{});body=payload.get('body',{})
  if isinstance(body,str):
   try:body=json.loads(body)
   except ValueError:body={'message':body}
  if not isinstance(body,dict):body={}
  logger=payload.get('logger_name') or body.get('logger_name')
  service=entry.get('resource',{}).get('labels',{}).get('service_name') or entry.get('labels',{}).get('service.name')
  rows.append({'timestamp':entry.get('timestamp'),'service':service,'logger':logger,
   'traceId':entry.get('trace','').split('/')[-1],'spanId':entry.get('spanId'),
   'severity':entry.get('severity') or payload.get('level') or body.get('level'),
   'message':clean(payload.get('message') or payload.get('msg') or body.get('message') or body.get('stringValue') or entry.get('textPayload') or ''),
   'httpStatus':entry.get('httpRequest',{}).get('status')})
 observed={r['traceId'] for r in rows}
 result={'status':'COLLECTED','testedTraceCount':len(traces),'observedTraceCount':len(observed),'logEntries':len(rows),'limited':bool(data.get('nextPageToken')),'scope':'At most 1000 stored entries from actual tested trace IDs; absence is reported, not counted as proof of stored logs.','requests':[{'name':r['name'],'traceId':r['traceId'],'logStatus':'FOUND' if r['traceId'] in observed else 'NO_LOGS_OBSERVED','links':r['links']} for r in tested],'entries':rows}
 (out/'api-logs.json').write_text(json.dumps(result,indent=2))
 lines=['# Stored API log evidence','',f'{len(rows)} sanitized stored entries; {len(observed)}/{len(traces)} tested traces observed. Limited: {result["limited"]}.','',result['scope'],'','| Request | Stored logs | Links |','| --- | --- | --- |']
 for r in result['requests']:lines.append(f'| {r["name"]} | {r["logStatus"]} | [Logs]({r["links"].get("logs","")}) · [Trace]({r["links"].get("trace","")}) |')
 for row in rows:lines+=['',f'## {row["timestamp"]} — {row["service"] or "service not labeled"} — {row["severity"]}',f'Trace: `{row["traceId"]}`','', '```text',row['message'].replace('```','[fence]'),'```']
 (out/'api-logs.md').write_text('\n'.join(lines)+'\n');print(json.dumps({k:result[k] for k in ['status','testedTraceCount','observedTraceCount','logEntries','limited']}))
 index=out/'index.html'
 if index.exists():
  html=index.read_text()
  if 'href="api-logs.md"' not in html:html=html.replace('<input id="filter"','<p><a href="api-logs.md">Stored API log evidence</a> · <a href="api-logs.json">Sanitized log JSON</a></p><input id="filter"')
  index.write_text(html)
if __name__=='__main__':main()
