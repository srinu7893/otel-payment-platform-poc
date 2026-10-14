#!/usr/bin/env python3
"""Read an existing revision and its bounded logs; no cloud mutations."""
import argparse
import json
from pathlib import Path
import re
import subprocess
from deploy_service import PROJECT, service_name
from run_release import SERVICES

def sanitize(message):
    message = re.sub(r'(?i)Bearer\s+[^\s,;"\']+', 'Bearer [REDACTED]', message)
    message = re.sub(r'(?i)(password|secret|access_token|id_token|authorization)\s*[=:]\s*[^\s,;]+', r'\1=[REDACTED]', message)
    return re.sub(r'jdbc:[^\s"\']+', '[JDBC REDACTED]', message)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--service',required=True,choices=[service_name(s) for s in SERVICES])
    p.add_argument('--revision',required=True)
    a=p.parse_args()
    if not re.fullmatch(re.escape(a.service)+r'-[a-z0-9-]+',a.revision):p.error('Revision must belong to the selected service')
    out=Path('artifacts/revision-diagnostics');out.mkdir(parents=True,exist_ok=True)
    revision=json.loads(subprocess.check_output(['gcloud','run','revisions','describe',a.revision,'--project='+PROJECT,'--region=us-central1','--format=json'],text=True))
    status={'service':a.service,'revision':a.revision,'status':revision.get('status',{})}
    print(json.dumps(status,indent=2));(out/'status.json').write_text(json.dumps(status,indent=2))
    query=f'resource.type="cloud_run_revision" AND resource.labels.service_name="{a.service}" AND resource.labels.revision_name="{a.revision}"'
    try:
        logs=json.loads(subprocess.check_output(['gcloud','logging','read',query,'--project='+PROJECT,'--limit=100','--order=asc','--format=json'],text=True,stderr=subprocess.PIPE))
    except subprocess.CalledProcessError as error:
        print('::warning::Cannot read container logs; deployment identity may need roles/logging.viewer. '+sanitize(error.stderr or ''))
        return
    safe=[{'timestamp':entry.get('timestamp'),'severity':entry.get('severity'),
           'message':sanitize(entry.get('textPayload') or str(entry.get('jsonPayload',{}).get('message','')))} for entry in logs]
    (out/'logs.json').write_text(json.dumps(safe,indent=2));print(json.dumps(safe,indent=2))

if __name__=='__main__':main()
