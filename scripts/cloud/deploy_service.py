#!/usr/bin/env python3
"""Deploy one prebuilt application digest, preserving traffic until readiness passes."""
import argparse
import copy
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlparse
from run_release import SERVICES, CALLS, DATABASE, JWT, SECRET_RE, render, gcloud, allocation, health

PROJECT = 'project-c9bd3d0e-266f-47bf-852'
REGISTRY = f'us-central1-docker.pkg.dev/{PROJECT}/otel-payment-platform'

def service_name(service):
    return 'payment-frontend' if service == 'frontend' else service

def validate_selected(config, service):
    """Validate only configuration consumed by this application's manifest."""
    if service not in SERVICES:
        raise ValueError('Unknown service')
    def value(value, pattern, label):
        if not isinstance(value, str) or 'replace-' in value or not re.fullmatch(pattern, value):
            raise ValueError('Invalid ' + label)
    for key, pattern in [('project', r'[a-z][a-z0-9-]{4,61}[a-z0-9]'),
                         ('region', r'[a-z]+-[a-z]+[0-9]'), ('repository', r'[a-z][a-z0-9_-]+')]:
        value(config.get(key), pattern, key)
    for key in ['urls', 'serviceAccounts']:
        if not isinstance(config.get(key), dict):
            raise ValueError('Expected object: ' + key)
    # Optional dependency URLs are validated when supplied, never required to bootstrap.
    targets = set(CALLS.get(service, [])) | ({'api-gateway'} if service == 'frontend' else set())
    for target in {service} | (targets & config['urls'].keys()):
        origin = config['urls'].get(target)
        if not isinstance(origin, str) or 'replace-' in origin:
            raise ValueError('Invalid Cloud Run URL: ' + target)
        url = urlparse(origin)
        if url.scheme != 'https' or not (url.hostname or '').endswith('.run.app') or url.netloc != url.hostname or url.path or url.query or url.fragment:
            raise ValueError('Expected canonical Cloud Run origin: ' + target)
    value(config['serviceAccounts'].get(service), r'[a-z][a-z0-9-]+@' + re.escape(config['project']) + r'\.iam\.gserviceaccount\.com', 'selected runtime account')
    needs_secrets = service in DATABASE or service in JWT or (service != 'frontend' and bool(config.get('grafana')))
    if needs_secrets:
        value(config.get('secretVersion'), r'[1-9][0-9]*', 'pinned numeric secretVersion')
    def secrets(key, names):
        group = config.get(key)
        if not isinstance(group, dict):
            raise ValueError('Expected secret object: ' + key)
        for name in names:
            value(group.get(name), SECRET_RE, key + '.' + name)
    if service in DATABASE:
        secrets('databaseSecrets', ['url', 'username', 'password'])
    if service in JWT:
        value(config.get('jwtSecret'), SECRET_RE, 'jwtSecret')
    if service in {'payment-service', 'notification-service'}:
        value(config.get('rabbitHost'), r'[a-zA-Z0-9.-]+', 'rabbitHost')
        secrets('rabbitSecrets', ['username', 'password'])
    if config.get('network') or config.get('subnetwork'):
        for key in ['network', 'subnetwork']:
            value(config.get(key), r'[a-z][a-z0-9-]+', key)
    if service != 'frontend' and config.get('grafana'):
        grafana = config['grafana']
        if not isinstance(grafana, dict):
            raise ValueError('Expected Grafana configuration object')
        value(grafana.get('endpoint'), r'https://[a-zA-Z0-9.-]+\.grafana\.net/otlp', 'Grafana endpoint')
        value(grafana.get('username'), r'[0-9]+', 'Grafana username')
        value(grafana.get('tokenSecret'), SECRET_RE, 'Grafana token secret')
    return config

def selected_manifest(config, service, image, collector, revision):
    validate_selected(config, service)
    if (config['project'], config['region'], config['repository']) != (PROJECT, 'us-central1', 'otel-payment-platform'):
        raise ValueError('Use the requested project, region and Artifact Registry repository')
    manifest = render(config, service, image, collector, revision)
    name = service_name(service)
    manifest['metadata']['name'] = name
    manifest['spec']['template']['metadata']['name'] = f'{name}-{revision}'
    # Cloud Run injects PORT into the ingress container; never set reserved PORT explicitly.
    app = manifest['spec']['template']['spec']['containers'][0]
    app['env'] = [entry for entry in app['env'] if entry['name'] != 'PORT']
    return manifest

def deploy(config, service, image, collector, revision, out):
    manifest = selected_manifest(config, service, image, collector, revision)
    name = service_name(service)
    previous = gcloud(config, 'run', 'services', 'describe', name)
    if previous['status']['url'] != config['urls'][service]:
        raise ValueError('Configured canonical URL differs from the selected live service')
    if not service in {'frontend', 'api-gateway'} and str(previous.get('metadata', {}).get('annotations', {}).get('run.googleapis.com/invoker-iam-disabled', 'false')).lower() == 'true':
        raise ValueError('Internal service must enforce invoker IAM')
    policy = gcloud(config, 'run', 'services', 'get-iam-policy', name)
    members = {m for b in policy.get('bindings', []) if b['role'] == 'roles/run.invoker' for m in b.get('members', [])}
    public = service in {'frontend', 'api-gateway'}
    if public and 'allUsers' not in members:
        raise ValueError('Public edge invoker policy must be provisioned first')
    if not public:
        if {'allUsers', 'allAuthenticatedUsers'} & members:
            raise ValueError('Internal service has public invoker access')
        required = {config['serviceAccounts'][service]}
        if any('serviceAccount:' + account not in members for account in required):
            raise ValueError('Provision selected service caller/self-invoker permissions first')
    traffic = allocation(previous)
    out.mkdir(parents=True, exist_ok=True)
    (out/'previous-traffic.json').write_text(json.dumps(traffic, indent=2))
    manifest['spec']['traffic'] = copy.deepcopy(previous['status'].get('traffic', []))
    for entry in manifest['spec']['traffic']:
        entry.pop('url', None)
    file = out/(service+'.json')
    file.write_text(json.dumps(manifest, indent=2))
    candidate = manifest['spec']['template']['metadata']['name']
    tag = 'verify-' + os.environ.get('GITHUB_RUN_ID', 'manual')
    promoted = False
    try:
        gcloud(config, 'run', 'services', 'replace', str(file))
        gcloud(config, 'run', 'services', 'update-traffic', name, '--update-tags='+tag+'='+candidate)
        ready = gcloud(config, 'run', 'services', 'describe', name)
        tagged_url = next(t['url'] for t in ready['status']['traffic'] if t.get('tag') == tag)
        health(config, tagged_url, service)
        promoted = True  # Record before API invocation: a timeout can follow a successful write.
        gcloud(config, 'run', 'services', 'update-traffic', name, '--to-revisions='+candidate+'=100')
        current = gcloud(config, 'run', 'services', 'describe', name)
        if allocation(current) != {candidate: 100}:
            raise RuntimeError('Selected service traffic did not converge')
        health(config, config['urls'][service], service)
        (out/'result.json').write_text(json.dumps({'status':'PASS','service':name,'image':image,'revision':candidate,'url':config['urls'][service]}, indent=2))
    except Exception:
        if promoted:
            split = ','.join(f'{key}={value}' for key, value in traffic.items())
            try:
                gcloud(config, 'run', 'services', 'update-traffic', name, '--to-revisions='+split)
            except subprocess.CalledProcessError:
                print('::error::Rollback failed; restore saved previous-traffic.json')
        raise
    finally:
        try:
            gcloud(config, 'run', 'services', 'update-traffic', name, '--remove-tags='+tag)
        except subprocess.CalledProcessError:
            print('::warning::Remove the selected service verification tag manually')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command', choices=['validate','deploy'])
    p.add_argument('--service', required=True, choices=SERVICES)
    p.add_argument('--config', required=True)
    p.add_argument('--image', required=True)
    p.add_argument('--collector', default='')
    p.add_argument('--revision', default='local-check')
    p.add_argument('--out', default='artifacts/service-release')
    a=p.parse_args()
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,18}', a.revision):
        raise ValueError('Invalid revision suffix')
    config=json.loads(Path(a.config).read_text())
    manifest=selected_manifest(config,a.service,a.image,a.collector,a.revision)
    if a.command=='validate':
        out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
        (out/(a.service+'.json')).write_text(json.dumps(manifest,indent=2))
        print('Selected service manifest validated; cloud state has not been checked')
    else:
        deploy(config,a.service,a.image,a.collector,a.revision,Path(a.out))

if __name__=='__main__':
    main()
