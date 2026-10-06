#!/usr/bin/env python3
"""Push locally built exact-commit images and record resolved registry digests."""
import argparse,json,os,re,subprocess
from pathlib import Path
from run_release import SERVICES
p=argparse.ArgumentParser();p.add_argument('--config',required=True);p.add_argument('--out',required=True);args=p.parse_args()
config=json.loads(Path(args.config).read_text())
for key,pattern in {'project':r'[a-z][a-z0-9-]{4,61}[a-z0-9]','region':r'[a-z]+-[a-z]+[0-9]+','repository':r'[a-z][a-z0-9_-]*'}.items():
    if not isinstance(config.get(key),str) or not re.fullmatch(pattern,config[key]): raise ValueError('Invalid registry '+key)
sha=os.environ['COMMIT_SHA']
if not re.fullmatch(r'[a-f0-9]{40}',sha): raise ValueError('Expected full source commit SHA')
host=config['region']+'-docker.pkg.dev'
subprocess.run(['gcloud','auth','configure-docker',host,'--quiet'],check=True)
images={}
for service in [*SERVICES,'collector']:
    local='otel-'+service+':'+sha
    metadata=json.loads(subprocess.check_output(['docker','image','inspect',local],text=True))[0]
    if metadata['Config']['Labels'].get('org.opencontainers.image.revision')!=sha: raise ValueError('Image revision label mismatch')
    tag=host+'/'+config['project']+'/'+config['repository']+'/'+service+':'+sha
    subprocess.run(['docker','tag',local,tag],check=True);subprocess.run(['docker','push',tag],check=True)
    digest=subprocess.check_output(['gcloud','artifacts','docker','images','describe',tag,'--project='+config['project'],'--format=value(image_summary.digest)'],text=True).strip()
    if not re.fullmatch(r'sha256:[a-f0-9]{64}',digest): raise ValueError('Registry returned invalid digest')
    images[service]=tag.rsplit(':',1)[0]+'@'+digest
Path(args.out).parent.mkdir(parents=True,exist_ok=True)
Path(args.out).write_text(json.dumps(images,indent=2))
Path('artifacts/cloud-release').mkdir(parents=True,exist_ok=True)
Path('artifacts/cloud-release/images.json').write_text(json.dumps(images,indent=2))
