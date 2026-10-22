"""Read-only final POC inventory. Never grants IAM, deploys, or reveals channel labels."""
import argparse,json,re,subprocess,urllib.request
from datetime import datetime,timezone
from pathlib import Path
from otel_access import SIGNALS,SCOPES

SERVICES=['auth-service','customer-service','mock-bank-service','gateway-service','payment-service','notification-service','api-gateway','payment-frontend']

def service_summary(name,live):
 spec=live.get('spec',{});template=spec.get('template',{});containers=template.get('spec',{}).get('containers',[])
 app=next((c for c in containers if c.get('name')=='app'),containers[0] if containers else {})
 attrs=next((e.get('value','') for e in app.get('env',[]) if e['name']=='OTEL_RESOURCE_ATTRIBUTES'),'')
 version=next((a.split('=',1)[1] for a in attrs.split(',') if a.startswith('service.version=')),None)
 status=live.get('status',{});conditions=status.get('conditions',[])
 return {'service':name,'ready':any(c.get('type')=='Ready' and str(c.get('status')).lower()=='true' for c in conditions),
  'latestReadyRevision':status.get('latestReadyRevisionName'),'url':status.get('url'),'image':app.get('image'),
  'otelServiceVersion':version,'minimumInstances':template.get('metadata',{}).get('annotations',{}).get('autoscaling.knative.dev/minScale','0'),
  'traffic':[{'revision':t.get('revisionName'),'percent':t.get('percent',0),'tag':t.get('tag')} for t in status.get('traffic',[])]}

def alert_summary(channels,policies):
 # Channel labels can contain email addresses, webhook URLs and secrets. Never export them.
 eligible={c['name'] for c in channels if c.get('enabled',True) and c.get('verificationStatus')!='UNVERIFIED'}
 managed=[p for p in policies if p.get('userLabels',{}).get('managed_by')=='otel-payment-poc']
 active=[p for p in managed if p.get('enabled',True) and set(p.get('notificationChannels',[]))&eligible]
 return {'channels':[{'name':c['name'],'type':c.get('type'),'enabled':c.get('enabled',True),'verificationStatus':c.get('verificationStatus','UNSPECIFIED')} for c in channels],
  'managedPolicyCount':len(managed),'enabledPoliciesWithEligibleChannel':len(active),
  'activation':'CONFIGURED' if active else 'PENDING','deliveryEvidence':'NOT_VERIFIED_BY_INVENTORY'}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',default='project-c9bd3d0e-266f-47bf-852');p.add_argument('--region',default='us-central1');a=p.parse_args()
 if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',a.project) or not re.fullmatch(r'[a-z]+-[a-z]+[0-9]',a.region):raise ValueError('Invalid project/region')
 token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
 def api(url,body=None):
  req=urllib.request.Request(url,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
  with urllib.request.urlopen(req,timeout=60) as response:return json.load(response)
 permissions=SIGNALS|SCOPES['alerts']|{'monitoring.notificationChannels.list'}
 granted=set(api('https://cloudresourcemanager.googleapis.com/v3/projects/'+a.project+':testIamPermissions',{'permissions':sorted(permissions)}).get('permissions',[]))
 result={'checkedAt':datetime.now(timezone.utc).isoformat(),'project':a.project,'scope':'Read-only configuration inventory; does not prove stored telemetry, private IAM boundaries or notification delivery.',
  'missingSignalPermissions':sorted(SIGNALS-granted),'missingAlertPermissions':sorted(SCOPES['alerts']-granted),'services':[]}
 failures=[]
 for name in SERVICES:
  read=subprocess.run(['gcloud','run','services','describe',name,'--project='+a.project,'--region='+a.region,'--format=json'],capture_output=True,text=True)
  if read.returncode:failures.append(name);result['services'].append({'service':name,'status':'UNREADABLE'})
  else:result['services'].append(service_summary(name,json.loads(read.stdout)))
 def listing(resource,key):
  from urllib.parse import urlencode
  rows=[];page=''
  for _ in range(20):
   data=api('https://monitoring.googleapis.com/v3/projects/'+a.project+'/'+resource+'?'+urlencode({'pageSize':100,**({'pageToken':page} if page else {})}))
   rows.extend(data.get(key,[]));page=data.get('nextPageToken','')
   if not page:return rows
  raise ValueError('Inventory exceeded pagination limit; refusing incomplete evidence')
 if {'monitoring.notificationChannels.list','monitoring.alertPolicies.list'}<=granted:
  result['alerts']=alert_summary(listing('notificationChannels','notificationChannels'),listing('alertPolicies','alertPolicies'))
 else:result['alerts']={'activation':'UNKNOWN_MISSING_READ_ACCESS','deliveryEvidence':'NOT_VERIFIED_BY_INVENTORY'}
 result['status']='CONFIGURATION_CHECK_PASSED' if not failures and not result['missingSignalPermissions'] and all(s.get('ready') for s in result['services']) else 'NEEDS_ATTENTION'
 out=Path('artifacts/poc-readiness');out.mkdir(parents=True,exist_ok=True);(out/'results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
 print(json.dumps(result,indent=2));return 0 if result['status']=='CONFIGURATION_CHECK_PASSED' else 1

if __name__=='__main__':raise SystemExit(main())
