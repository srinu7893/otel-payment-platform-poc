#!/usr/bin/env python3
"""Deploy one prebuilt application digest, preserving traffic until readiness passes."""
import argparse
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
from urllib.parse import urlparse
from run_release import SERVICES, CALLS, DATABASE, JWT, SECRET_RE, PUBLIC, render, gcloud, allocation, health, identity_token, preserved_traffic, validate_manifest_traffic

PROJECT = 'project-c9bd3d0e-266f-47bf-852'
REGISTRY = f'us-central1-docker.pkg.dev/{PROJECT}/otel-payment-platform'
DEPLOYER = 'github-deployer@' + PROJECT + '.iam.gserviceaccount.com'

def runtime_account(config, service):
    accounts = config.get('serviceAccounts', {})
    if not isinstance(accounts, dict): raise ValueError('Expected object: serviceAccounts')
    account = accounts[service] if service in accounts else config.get('defaultServiceAccount')
    if not account:
        raise ValueError('Set serviceAccounts[' + service + '] or defaultServiceAccount for the selected runtime identity')
    return account

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
    # Optional dependency URLs are validated when supplied, never required to bootstrap.
    targets = set(CALLS.get(service, [])) | ({'api-gateway'} if service == 'frontend' else set())
    urls = config.get('urls', {})
    if not isinstance(urls, dict):
        raise ValueError('Expected object: urls')
    for target in ({service} | targets) & urls.keys():
        origin = config['urls'].get(target)
        if not isinstance(origin, str) or 'replace-' in origin:
            raise ValueError('Invalid Cloud Run URL: ' + target)
        url = urlparse(origin)
        if url.scheme != 'https' or not (url.hostname or '').endswith('.run.app') or url.netloc != url.hostname or url.path or url.query or url.fragment:
            raise ValueError('Expected canonical Cloud Run origin: ' + target)
    value(runtime_account(config, service), r'[a-z][a-z0-9-]+@' + re.escape(config['project']) + r'\.iam\.gserviceaccount\.com', 'selected runtime account')
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
        value(config.get('rabbitVirtualHost'), r'[^\s]+', 'rabbitVirtualHost (CloudAMQP vhost is required)')
        if type(config.get('rabbitPort')) is not int or config['rabbitPort'] != 5671:
            raise ValueError('rabbitPort must be the integer TLS port 5671')
    if config.get('ingress', 'all') != 'all':
        raise ValueError('Only ingress=all is supported by this tagged-health topology')
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
    config = copy.deepcopy(config)
    config.setdefault('urls', {})
    config.setdefault('serviceAccounts', {})[service] = runtime_account(config, service)
    manifest = render(config, service, image, collector, revision)
    name = service_name(service)
    manifest['metadata']['name'] = name
    manifest['spec']['template']['metadata']['name'] = f'{name}-{revision}'
    # Cloud Run injects PORT into the ingress container; never set reserved PORT explicitly.
    app = manifest['spec']['template']['spec']['containers'][0]
    app['env'] = [entry for entry in app['env'] if entry['name'] != 'PORT']
    return manifest

def discover(config, service):
    """Read the selected canonical URL and any available outbound dependencies."""
    resolved = copy.deepcopy(config)
    resolved.setdefault('urls', {})
    resolved.setdefault('serviceAccounts', {})[service] = runtime_account(config, service)
    warnings = []
    try:
        previous = gcloud(config, 'run', 'services', 'describe', service_name(service))
    except subprocess.CalledProcessError as error:
        detail = str(error.stderr or '')
        if 'PERMISSION_DENIED' in detail or 'permission' in detail.lower():
            raise ValueError('Cannot inspect selected Cloud Run service; verify deployment identity has roles/run.viewer or roles/run.admin') from error
        raise ValueError(f'Cloud Run service {service_name(service)} is not bootstrapped in {config["project"]}/{config["region"]}. Run the documented bootstrap command first.') from error
    actual_account = previous.get('spec', {}).get('template', {}).get('spec', {}).get('serviceAccountName')
    expected_account = runtime_account(resolved, service)
    if actual_account != expected_account:
        raise ValueError(f'Runtime account mismatch for {service_name(service)}: expected {expected_account}, live {actual_account}; update the bootstrap identity before deployment')
    def canonical(target, live):
        actual = live.get('status', {}).get('url')
        if not actual: raise ValueError('Missing canonical status.url for ' + target)
        configured = resolved['urls'].get(target)
        if configured and configured != actual:
            warnings.append(f'{target}: configured URL {configured} differs from canonical URL {actual}; using status.url')
        resolved['urls'][target] = actual
    canonical(service, previous)
    for target in (CALLS.get(service, []) if service != 'frontend' else ['api-gateway']):
        try:
            live = gcloud(config, 'run', 'services', 'describe', service_name(target))
        except subprocess.CalledProcessError:
            warnings.append(target + ': dependency not discoverable; retaining supplied URL if present')
            continue
        canonical(target, live)
    validate_selected(resolved, service)
    return resolved, previous, warnings

def verify_runtime_permissions(config, service):
    """Verify actAs and private token creation before any revision write."""
    account = runtime_account(config, service)
    token = subprocess.check_output(['gcloud', 'auth', 'print-access-token'], text=True).strip()
    request = urllib.request.Request('https://iam.googleapis.com/v1/projects/-/serviceAccounts/' + account + ':testIamPermissions',
        data=json.dumps({'permissions': ['iam.serviceAccounts.actAs']}).encode(),
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            permissions = json.load(response).get('permissions', [])
    except OSError as error:
        raise ValueError('Cannot verify actAs; deployment identity needs roles/iam.serviceAccountUser on ' + account) from error
    if 'iam.serviceAccounts.actAs' not in permissions:
        raise ValueError('Missing actAs; deployment identity needs roles/iam.serviceAccountUser on ' + account)
    if service not in PUBLIC: identity_token(config, service)

def check_access(config, service, live, policy):
    disabled = str(live.get('metadata', {}).get('annotations', {}).get('run.googleapis.com/invoker-iam-disabled', 'false')).lower() == 'true'
    members = {m for b in policy.get('bindings', []) if b['role'] == 'roles/run.invoker' for m in b.get('members', [])}
    if service in PUBLIC:
        if not disabled and 'allUsers' not in members:
            raise ValueError('Public edge requires allUsers Invoker or disabled Invoker IAM check')
    else:
        if disabled: raise ValueError('Internal service must enforce invoker IAM')
        if {'allUsers', 'allAuthenticatedUsers'} & members:
            raise ValueError('Internal service has public invoker access')
        if 'serviceAccount:' + runtime_account(config, service) not in members:
            raise ValueError('Private health checks require roles/run.invoker for the selected runtime identity')
        if 'serviceAccount:' + DEPLOYER not in members:
            raise ValueError('Private deployment requires roles/run.invoker for ' + DEPLOYER)
    return disabled

def live_properties(live, policy):
    template = live.get('spec', {}).get('template', {})
    spec = template.get('spec', {})
    annotations = template.get('metadata', {}).get('annotations', {})
    metadata = live.get('metadata', {}).get('annotations', {})
    disabled = str(metadata.get('run.googleapis.com/invoker-iam-disabled','false')).lower()=='true'
    members = {m for b in policy.get('bindings', []) if b['role']=='roles/run.invoker' for m in b.get('members', [])}
    return {'canonicalUrl':live.get('status', {}).get('url'), 'runtimeServiceAccount':spec.get('serviceAccountName'),
            'traffic':preserved_traffic(live), 'latestReadyRevision':live.get('status', {}).get('latestReadyRevisionName') or live.get('status', {}).get('latest_ready_revision_name'),
            'ingress':metadata.get('run.googleapis.com/ingress','all'),
            'invokerIamDisabled':disabled, 'accessMode':'public' if disabled or 'allUsers' in members else 'private',
            'invokerBindings':[b for b in policy.get('bindings', []) if b['role']=='roles/run.invoker'],
            'minInstances':annotations.get('autoscaling.knative.dev/minScale','0'),
            'maxInstances':annotations.get('autoscaling.knative.dev/maxScale'),
            'cpuThrottling':annotations.get('run.googleapis.com/cpu-throttling','true'),
            'billingMode':'instance-based' if str(annotations.get('run.googleapis.com/cpu-throttling','true')).lower()=='false' else 'request-based',
            'serviceMinInstances':metadata.get('run.googleapis.com/minScale'),
            'serviceMaxInstances':metadata.get('run.googleapis.com/maxScale'),
            'containers':[{'name':c.get('name'), 'ports':c.get('ports', []), 'resources':c.get('resources', {}),
                'secretReferences':[{'name':e['name'],'valueFrom':e['valueFrom']} for e in c.get('env', []) if 'valueFrom' in e]} for c in spec.get('containers', [])]}

def deploy(config, service, image, collector, revision, out):
    validate_selected(config, service)
    configured_urls = copy.deepcopy(config.get('urls', {}))
    config, previous, warnings = discover(config, service)
    manifest = selected_manifest(config, service, image, collector, revision)
    name = service_name(service)
    policy = gcloud(config, 'run', 'services', 'get-iam-policy', name)
    disabled = check_access(config, service, previous, policy)
    if service in PUBLIC and disabled:
        manifest['metadata']['annotations']['run.googleapis.com/invoker-iam-disabled'] = 'true'
    out.mkdir(parents=True, exist_ok=True)
    (out/'discovery.json').write_text(json.dumps({'service':name, 'canonicalUrl':config['urls'][service], 'configuredUrls':configured_urls,
        'urls':config['urls'], 'warnings':warnings, 'liveService':live_properties(previous, policy)}, indent=2))
    for warning in warnings: print('::warning::' + warning)
    verify_runtime_permissions(config, service)
    targets = preserved_traffic(previous)
    traffic = allocation({'status': {'traffic': targets}})
    (out/'previous-traffic.json').write_text(json.dumps(traffic, indent=2))
    manifest['spec']['traffic'] = targets
    validate_manifest_traffic(manifest, traffic)
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
        result={'status':'PASS','service':name,'image':image,'revision':candidate,'url':config['urls'][service], 'candidateUrl':tagged_url, 'configuredUrls':configured_urls,'warnings':warnings}
        (out/'result.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result, indent=2))
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
