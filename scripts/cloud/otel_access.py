#!/usr/bin/env python3
"""Read-only effective-permission preflight. No payments, IAM writes or deployments."""
import argparse,json,os,re,subprocess,urllib.request,urllib.error
from pathlib import Path
from cloud_acceptance import google_failure

SIGNALS={'logging.logEntries.list','cloudtrace.traces.get','monitoring.timeSeries.list','run.services.get'}
DASHBOARDS={'monitoring.dashboards.list','monitoring.dashboards.get','monitoring.dashboards.create','monitoring.dashboards.update'}
SCOPES={'signals':SIGNALS,'metrics':{'monitoring.timeSeries.list'},'dashboards':DASHBOARDS|{'monitoring.timeSeries.list'},'all':SIGNALS|DASHBOARDS}
ROLES=['roles/logging.viewer','roles/cloudtrace.user','roles/monitoring.viewer','roles/monitoring.dashboardEditor']

def assess(granted,scope):
    required=SCOPES[scope];present=required&set(granted)
    return {'status':'PASS' if present==required else 'BLOCKED','scope':scope,
        'granted':sorted(present),'missing':sorted(required-present),
        'meaning':'Effective IAM permissions only; stored data and API/perimeter access need separate verification'}

def admin_commands(project,principal):
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',project):raise ValueError('Invalid project')
    if not re.fullmatch(r'[a-z][a-z0-9-]*@[a-z][a-z0-9-]*\.iam\.gserviceaccount\.com',principal):raise ValueError('Service-account email required')
    return [f'gcloud projects add-iam-policy-binding {project} --member=serviceAccount:{principal} --role={role} --condition=None' for role in ROLES]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--project',required=True)
    p.add_argument('--scope',choices=SCOPES,default='all');p.add_argument('--out',default='artifacts/otel-access/results.json')
    p.add_argument('--principal',default=os.getenv('OBSERVER_ACCOUNT',''))
    p.add_argument('--print-admin-commands',action='store_true');args=p.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9-]{4,61}[a-z0-9]',args.project) or args.project.startswith('replace-'):raise ValueError('Invalid project')
    if args.print_admin_commands:
        print('Administrator reference only. These commands have NOT been executed.')
        print('\n'.join(admin_commands(args.project,args.principal)));return 0
    token=subprocess.check_output(['gcloud','auth','print-access-token'],text=True).strip()
    request=urllib.request.Request(f'https://cloudresourcemanager.googleapis.com/v3/projects/{args.project}:testIamPermissions',
        data=json.dumps({'permissions':sorted(SCOPES[args.scope])}).encode(),
        headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(request,timeout=60) as response:data=json.load(response)
        result=assess(data.get('permissions',[]),args.scope)
    except urllib.error.HTTPError as error:result={**google_failure(error.code,error.read()),'scope':args.scope,'meaning':'Permission preflight could not be completed'}
    result.update(project=args.project,principal=args.principal or 'Current gcloud identity')
    out=Path(args.out);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result,indent=2))
    return 0 if result['status']=='PASS' else 2 if result['status']=='BLOCKED' else 1

if __name__=='__main__':raise SystemExit(main())
