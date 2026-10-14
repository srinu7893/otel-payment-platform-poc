#!/usr/bin/env python3
"""Read-only bootstrap audit, or generate commands/manifests; never create resources."""
import argparse
import json
from pathlib import Path
import shlex
from decimal import Decimal
from deploy_service import (SERVICES, PROJECT, PUBLIC, service_name, validate_selected,
                            selected_manifest, discover, check_access, verify_runtime_permissions, runtime_account)
from run_release import gcloud

DEPLOYER = 'github-deployer@' + PROJECT + '.iam.gserviceaccount.com'

def resource_limits(limits):
    cpu = str(limits.get('cpu', '0'))
    cores = Decimal(cpu[:-1])/1000 if cpu.endswith('m') else Decimal(cpu)
    memory = str(limits.get('memory', '0'))
    for suffix, multiplier in [('Gi',2**30),('Mi',2**20),('Ki',2**10),('G',10**9),('M',10**6),('K',10**3)]:
        if memory.endswith(suffix): return cores, Decimal(memory[:-len(suffix)])*multiplier
    return cores, Decimal(memory)

def audit(config, service, live, policy, collector):
    check_access(config, service, live, policy)
    template = live.get('spec', {}).get('template', {})
    spec = template.get('spec', {})
    if spec.get('serviceAccountName') != runtime_account(config, service):
        raise ValueError('Bootstrap runtime service account does not match selected configuration')
    containers = spec.get('containers', [])
    if not containers: raise ValueError('Bootstrap has no application container')
    expected = selected_manifest(config, service, containers[0]['image'], collector, 'bootstrap-check')
    wanted = expected['spec']['template']
    ports = containers[0].get('ports', [])
    if len(ports) != 1 or ports[0].get('containerPort') != 8080:
        raise ValueError('Bootstrap application must listen on container port 8080')
    for key in ['autoscaling.knative.dev/minScale', 'autoscaling.knative.dev/maxScale', 'run.googleapis.com/cpu-throttling', 'run.googleapis.com/execution-environment']:
        if template.get('metadata', {}).get('annotations', {}).get(key) != wanted['metadata']['annotations'][key]:
            raise ValueError('Bootstrap scaling/billing mismatch: ' + key)
    if live.get('metadata', {}).get('annotations', {}).get('run.googleapis.com/ingress', 'all') != 'all':
        raise ValueError('Bootstrap ingress must be all for this verification topology')
    if len(containers) != len(wanted['spec']['containers']):
        raise ValueError('Bootstrap container count does not match app/Collector topology')
    for actual, target in zip(containers, wanted['spec']['containers']):
        if resource_limits(actual.get('resources', {}).get('limits', {})) != resource_limits(target['resources']['limits']):
            raise ValueError('Bootstrap CPU/memory does not match selected manifest')
        for probe in ['startupProbe', 'livenessProbe']:
            if probe in target:
                actual_http = actual.get(probe, {}).get('httpGet', {})
                if any(actual_http.get(key) != value for key,value in target[probe]['httpGet'].items()) or actual_http.get('scheme','HTTP') != 'HTTP':
                    raise ValueError('Bootstrap probe mismatch: ' + probe)
        refs = {e['name']:e['valueFrom'] for e in actual.get('env', []) if 'valueFrom' in e}
        expected_refs = {e['name']:e['valueFrom'] for e in target.get('env', []) if 'valueFrom' in e}
        if refs != expected_refs: raise ValueError('Bootstrap secret names/versions do not match selected configuration')
        actual_env={e['name']:e.get('value') for e in actual.get('env', [])}
        if service == 'frontend' and any(name == 'JWT_SECRET' or name.startswith(('SPRING_DATASOURCE_', 'SPRING_RABBITMQ_')) for name in actual_env):
            raise ValueError('Frontend must not contain datasource, broker or JWT signing configuration')
        for entry in target.get('env', []):
            if entry['name'] in {'OTEL_SERVICE_NAME','OTEL_EXPORTER_OTLP_ENDPOINT','OTEL_EXPORTER_OTLP_PROTOCOL','OTEL_TRACES_EXPORTER','OTEL_LOGS_EXPORTER','OTEL_METRICS_EXPORTER'} and actual_env.get(entry['name']) != entry.get('value'):
                raise ValueError('Bootstrap OpenTelemetry setting mismatch: ' + entry['name'])
        if target['name']=='collector' and actual['image'] != collector:
            raise ValueError('Bootstrap Collector image does not match immutable COLLECTOR_IMAGE')
    members={m for b in policy.get('bindings', []) if b['role']=='roles/run.invoker' for m in b.get('members', [])}
    if service not in PUBLIC and 'serviceAccount:'+DEPLOYER not in members:
        raise ValueError('Bootstrap requires roles/run.invoker for github-deployer on private service')
    return {'status':'PASS', 'service':service_name(service), 'canonicalUrl':config['urls'][service],
            'scope':'service manifest and service-level Invoker policy; secret access/export delivery require live acceptance'}

def commands(config, service, image, collector, out):
    manifest = selected_manifest(config, service, image, collector, 'bootstrap-1')
    out.mkdir(parents=True, exist_ok=True)
    path=out/(service+'.json');path.write_text(json.dumps(manifest,indent=2))
    name=service_name(service)
    base=f'--project={config["project"]} --region={config["region"]}'
    result=[f'gcloud run services replace {shlex.quote(path.as_posix())} {base}']
    members=['allUsers'] if service in PUBLIC else ['serviceAccount:'+runtime_account(config, service), 'serviceAccount:'+DEPLOYER]
    for member in members:
        result.append(f'gcloud run services add-iam-policy-binding {name} {base} --member={member} --role=roles/run.invoker')
    result.append(f'gcloud run services describe {name} {base} --format="value(status.url)"')
    (out/(service+'.commands.sh')).write_text('\n'.join(result)+'\n')
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['check','commands'])
    parser.add_argument('--service',choices=SERVICES,required=True)
    parser.add_argument('--config',required=True)
    parser.add_argument('--image')
    parser.add_argument('--collector',default='')
    parser.add_argument('--out',default='artifacts/bootstrap')
    parser.add_argument('--check-deployer-permissions',action='store_true')
    args=parser.parse_args()
    config=json.loads(Path(args.config).read_text());validate_selected(config,args.service)
    if args.command=='commands':
        if not args.image:parser.error('--image requires an existing immutable application digest')
        print('\n'.join(commands(config,args.service,args.image,args.collector,Path(args.out))))
    else:
        config,live,warnings=discover(config,args.service)
        policy=gcloud(config,'run','services','get-iam-policy',service_name(args.service))
        result=audit(config,args.service,live,policy,args.collector)
        if args.check_deployer_permissions:verify_runtime_permissions(config,args.service)
        result['warnings']=warnings
        out=Path(args.out);out.mkdir(parents=True,exist_ok=True)
        (out/(args.service+'.audit.json')).write_text(json.dumps(result,indent=2))
        print(json.dumps(result,indent=2))

if __name__=='__main__':main()
