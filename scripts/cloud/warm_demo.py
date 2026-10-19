#!/usr/bin/env python3
"""One-off readiness warmup; no deployment, scheduler, config, or money changes."""
import concurrent.futures,json,subprocess,time
from pathlib import Path
from cloud_acceptance import request,validate_config
from otel_dashboards import JAVA_SERVICES
PROJECT='project-c9bd3d0e-266f-47bf-852'
def warm(service):
    started=time.monotonic()
    live=json.loads(subprocess.check_output(['gcloud','run','services','describe',service,'--project='+PROJECT,'--region=us-central1','--format=json'],text=True))
    origin=live['status']['url'];validate_config({'project':PROJECT,'apiUrl':origin})
    headers={}
    if service!='api-gateway':
        account=live['spec']['template']['spec']['serviceAccountName']
        token=subprocess.check_output(['gcloud','auth','print-identity-token','--impersonate-service-account='+account,'--audiences='+origin,'--include-email'],text=True).strip()
        headers={'X-Serverless-Authorization':'Bearer '+token}
    try:
        data=request(origin+'/actuator/health/readiness',headers=headers)
        assert data['status']=='UP'
        return {'service':service,'status':'PASS','seconds':round(time.monotonic()-started,2)}
    except Exception as error:return {'service':service,'status':'FAIL','reason':type(error).__name__}
def main():
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(warm,JAVA_SERVICES))
    root=Path('artifacts/demo-warmup');root.mkdir(parents=True,exist_ok=True);(root/'results.json').write_text(json.dumps(results,indent=2))
    print(json.dumps(results,indent=2))
    return 1 if any(row['status']!='PASS' for row in results) else 0
if __name__=='__main__':raise SystemExit(main())
