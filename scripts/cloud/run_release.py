#!/usr/bin/env python3
"""Render/validate or release existing Cloud Run services; no implicit infrastructure creation."""
import argparse
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
from urllib.parse import urlparse

SERVICES = ['mock-bank-service', 'customer-service', 'auth-service', 'gateway-service',
            'notification-service', 'payment-service', 'api-gateway', 'frontend']
DATABASE = {'auth-service', 'customer-service', 'mock-bank-service', 'payment-service', 'notification-service'}
CALLS = {'auth-service': ['customer-service', 'mock-bank-service'], 'payment-service': ['gateway-service', 'customer-service'], 'gateway-service': ['mock-bank-service'],
         'api-gateway': SERVICES[:6]}
JWT = {'auth-service', 'payment-service', 'notification-service', 'api-gateway'}
SECRET_RE = r'[A-Za-z0-9_-]+'
PUBLIC = {'frontend', 'api-gateway'}
WORKERS = {'payment-service', 'notification-service'}

def validate(config, allow_example=False):
    if config.get('defaultServiceAccount') and not allow_example:
        # The retired manual validator can still check the stable shared-account format.
        from deploy_service import validate_selected, runtime_account
        normalized = copy.deepcopy(config)
        normalized.setdefault('urls', {})
        normalized.setdefault('serviceAccounts', {})
        normalized.setdefault('prefix', 'otel-poc')
        for service in SERVICES:
            validate_selected(normalized, service)
            normalized['serviceAccounts'][service] = runtime_account(normalized, service)
        return normalized
    raw = json.dumps(config)
    if not allow_example and 'replace-' in raw:
        raise ValueError('Example placeholders must be replaced before deployment')
    for key, pattern in {'project': r'[a-z][a-z0-9-]{4,61}[a-z0-9]', 'region': r'[a-z]+-[a-z]+[0-9]',
                         'repository': r'[a-z][a-z0-9_-]+', 'prefix': r'[a-z][a-z0-9-]{0,20}',
                         'network': r'[a-z][a-z0-9-]+', 'subnetwork': r'[a-z][a-z0-9-]+'}.items():
        if not re.fullmatch(pattern, config.get(key, '')): raise ValueError('Invalid ' + key)
    if not re.fullmatch(r'[1-9][0-9]*', config.get('secretVersion', '')):
        raise ValueError('Pin a numerical Secret Manager version; latest is not reproducible')
    for service in SERVICES:
        url = urlparse(config['urls'][service])
        if url.scheme != 'https' or not (url.hostname or '').endswith('.run.app') or url.netloc != url.hostname or url.path or url.query or url.fragment:
            raise ValueError('Expected canonical Cloud Run origin: ' + service)
        account = config['serviceAccounts'][service]
        if not re.fullmatch(r'[a-z][a-z0-9-]+@' + re.escape(config['project']) + r'\.iam\.gserviceaccount\.com', account):
            raise ValueError('Invalid per-service runtime account: ' + service)
    if len(set(config['serviceAccounts'].values())) != len(SERVICES):
        raise ValueError('Use distinct runtime service accounts')
    if not re.fullmatch(r'[a-zA-Z0-9.-]+', config.get('rabbitHost', '')):
        raise ValueError('Rabbit host must not include credentials')
    for value in [*config['databaseSecrets'].values(), *config['rabbitSecrets'].values(), config['jwtSecret']]:
        if not re.fullmatch(SECRET_RE, value): raise ValueError('Invalid secret name')
    grafana = config.get('grafana')
    if grafana:
        endpoint = urlparse(grafana.get('endpoint', ''))
        if endpoint.scheme != 'https' or not (endpoint.hostname or '').endswith('.grafana.net') or endpoint.netloc != endpoint.hostname or endpoint.query or endpoint.fragment or endpoint.path != '/otlp':
            raise ValueError('Expected the selected Grafana Cloud HTTPS OTLP gateway endpoint')
        if not re.fullmatch(r'[0-9]+', grafana.get('username', '')) or not re.fullmatch(SECRET_RE, grafana.get('tokenSecret', '')):
            raise ValueError('Grafana needs an instance ID and a Secret Manager token reference')
    return config

def secret(config, name, key):
    return {'name': name, 'valueFrom': {'secretKeyRef': {'name': key, 'key': config['secretVersion']}}}

def render(config, service, image, collector, revision):
    for digest in ([image] if service == 'frontend' else [image, collector]):
        expected = f"{config['region']}-docker.pkg.dev/{config['project']}/{config['repository']}/"
        if not digest.startswith(expected) or not re.fullmatch(r'.+@sha256:[a-f0-9]{64}', digest):
            raise ValueError('Images require immutable digests in the configured registry')
    env = {'MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE': 'health,info'}
    secrets = []
    if service in DATABASE:
        for name, key in [('SPRING_DATASOURCE_URL', 'url'), ('SPRING_DATASOURCE_USERNAME', 'username'), ('SPRING_DATASOURCE_PASSWORD', 'password')]:
            secrets.append(secret(config, name, config['databaseSecrets'][key]))
        env.update({'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE': '5', 'SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE': '1'})
    if service in JWT: secrets.append(secret(config, 'JWT_SECRET', config['jwtSecret']))
    if service in {'payment-service', 'notification-service'}:
        env.update({'SPRING_RABBITMQ_HOST': config['rabbitHost'], 'SPRING_RABBITMQ_PORT': str(config.get('rabbitPort', 5671)), 'SPRING_RABBITMQ_SSL_ENABLED': 'true',
                    'SPRING_RABBITMQ_VIRTUAL_HOST': config.get('rabbitVirtualHost', '/')})
        for name, key in [('SPRING_RABBITMQ_USERNAME', 'username'), ('SPRING_RABBITMQ_PASSWORD', 'password')]:
            secrets.append(secret(config, name, config['rabbitSecrets'][key]))
    targets = [target for target in CALLS.get(service, []) if target in config['urls']]
    audiences = [config['urls'][target] for target in targets]
    if not audiences and service in config['urls']:
        audiences = [config['urls'][service]]
    if service in CALLS and audiences:
        env.update({'CLOUD_RUN_IAM_ENABLED': 'true', 'CLOUD_RUN_AUDIENCES': ','.join(audiences)})
    if service == 'auth-service':
        for key, target in [('CLIENTS_CUSTOMER_BASE_URL', 'customer-service'), ('CLIENTS_BANK_BASE_URL', 'mock-bank-service')]:
            if target in config['urls']: env[key] = config['urls'][target]
    if service == 'payment-service':
        for key, target in [('CLIENTS_GATEWAY_BASE_URL', 'gateway-service'), ('CLIENTS_CUSTOMER_BASE_URL', 'customer-service')]:
            if target in config['urls']: env[key] = config['urls'][target]
    if service == 'gateway-service' and 'mock-bank-service' in config['urls']: env['CLIENTS_BANK_BASE_URL'] = config['urls']['mock-bank-service']
    if service == 'api-gateway':
        origins=set(config.get('browserOrigins',[]))
        if config['urls'].get('frontend'):origins.add(config['urls']['frontend'])
        env['TELEMETRY_RUM_ALLOWED_ORIGINS']=','.join(sorted(origins))
        for key, target in [('AUTH', 'auth-service'), ('CUSTOMER', 'customer-service'), ('PAYMENT', 'payment-service'), ('NOTIFICATION', 'notification-service'), ('GATEWAY', 'gateway-service'), ('BANK', 'mock-bank-service')]:
            if target in config['urls']: env[f'SERVICES_{key}_URL'] = config['urls'][target]
    if service == 'frontend':
        env = {}
        if 'api-gateway' in config['urls']:
            env = {'API_GATEWAY_URL': config['urls']['api-gateway'], 'API_GATEWAY_HOST': urlparse(config['urls']['api-gateway']).hostname}
    else:
        env.update({'OTEL_SERVICE_NAME': service, 'OTEL_EXPORTER_OTLP_ENDPOINT': 'http://127.0.0.1:4318',
            'OTEL_EXPORTER_OTLP_PROTOCOL': 'http/protobuf', 'OTEL_TRACES_EXPORTER': 'otlp', 'OTEL_LOGS_EXPORTER': 'otlp',
            'OTEL_METRICS_EXPORTER': 'otlp', 'OTEL_PROPAGATORS': 'tracecontext,baggage', 'OTEL_METRIC_EXPORT_INTERVAL': '10000',
            'OTEL_INSTRUMENTATION_MICROMETER_ENABLED': 'true', 'OTEL_TRACES_SAMPLER': 'parentbased_traceidratio',
            'OTEL_TRACES_SAMPLER_ARG': '1.0', # Full PoC traces; sampling is a separate, tested policy decision.
            'OTEL_RESOURCE_ATTRIBUTES': f'service.namespace=payment-poc,deployment.environment.name=gcp-poc,service.version={os.getenv('COMMIT_SHA', revision)}',
            'MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED': 'true'})
    probe = '/health' if service == 'frontend' else '/actuator/health/liveness'
    containers = [{'name': 'app', 'image': image, 'ports': [{'containerPort': 8080}],
                   'resources': {'limits': {'cpu': '1', 'memory': '768Mi'}},
                   'env': [{'name': key, 'value': value} for key, value in env.items()] + secrets,
                   'startupProbe': {'httpGet': {'path': probe, 'port': 8080}, 'periodSeconds': 5, 'timeoutSeconds': 3, 'failureThreshold': 36},
                   'livenessProbe': {'httpGet': {'path': probe, 'port': 8080}, 'periodSeconds': 30, 'timeoutSeconds': 3}}]
    annotations = {'autoscaling.knative.dev/maxScale': '1', 'autoscaling.knative.dev/minScale': '0',
                   'run.googleapis.com/cpu-throttling': 'true' if service == 'frontend' else 'false',
                   'run.googleapis.com/startup-cpu-boost': 'true', 'run.googleapis.com/execution-environment': 'gen2'}
    if config.get('network') and config.get('subnetwork'):
        annotations.update({'run.googleapis.com/network-interfaces': json.dumps([{'network': config['network'], 'subnetwork': config['subnetwork']}]),
                            'run.googleapis.com/vpc-access-egress': 'private-ranges-only'})
    # minScale matters for the existing scheduler/consumer implementation. Revisions can overlap during a rollout.
    if service in {'payment-service', 'notification-service'}: annotations['autoscaling.knative.dev/minScale'] = '1'
    if service in config.get('warmServices',[]):annotations['autoscaling.knative.dev/minScale']='1'
    if service != 'frontend':
        annotations['run.googleapis.com/container-dependencies'] = json.dumps({'app': ['collector']})
        containers.append({'name': 'collector', 'image': collector, 'resources': {'limits': {'cpu': '1', 'memory': '256Mi'}},
                           'env': [{'name': 'GOOGLE_CLOUD_PROJECT', 'value': config['project']}, {'name':'APPLICATION_SERVICE','value':service}],
                           'startupProbe': {'httpGet': {'path': '/', 'port': 13133}, 'periodSeconds': 3, 'timeoutSeconds': 3, 'failureThreshold': 20}})
    if config.get('grafana') and service != 'frontend':
        sidecar = containers[-1]
        sidecar['args'] = ['--config=/etc/otelcol-contrib/config.yaml', '--config=/etc/otelcol-contrib/grafana.yaml']
        sidecar['env'].extend([
            {'name': 'GRAFANA_OTLP_ENDPOINT', 'value': config['grafana']['endpoint']},
            {'name': 'GRAFANA_OTLP_USERNAME', 'value': config['grafana']['username']},
            secret(config, 'GRAFANA_OTLP_TOKEN', config['grafana']['tokenSecret'])])
    if config.get('durableCollector') and service!='frontend':
        sidecar=containers[-1]
        sidecar['args']=['--config=/etc/otelcol-contrib/config.yaml','--config=/etc/otelcol-contrib/durable.yaml']
        sidecar['env'].extend([{'name':'DURABLE_COLLECTOR_ENDPOINT','value':config['durableCollector']['endpoint']},
                               secret(config,'DURABLE_COLLECTOR_TOKEN',config['durableCollector']['tokenSecret'])])
    prefix = config.get('prefix', 'otel-poc')
    return {'apiVersion': 'serving.knative.dev/v1', 'kind': 'Service', 'metadata': {'name': prefix + '-' + service,
              'annotations': {'run.googleapis.com/ingress': config.get('ingress', 'all'),
                              'run.googleapis.com/invoker-iam-disabled': 'false'}},
            'spec': {'template': {'metadata': {'name': prefix + '-' + service + '-' + revision,
                                'annotations': annotations}, 'spec': {'serviceAccountName': config['serviceAccounts'][service],
                                'containerConcurrency': 20, 'timeoutSeconds': 60, 'containers': containers}}}}

def gcloud(config, *args):
    try:
        output = subprocess.check_output(['gcloud', '--quiet', '--project=' + config['project'], '--format=json',
                                         *args, '--region=' + config['region']], text=True, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as error:
        if error.stderr: print(error.stderr.strip())
        raise
    return json.loads(output or 'null')

def allocation(service):
    traffic = {}
    for entry in service['status'].get('traffic', []):
        if entry.get('percent', 0):
            if not entry.get('revisionName'): raise ValueError('Unresolved current traffic')
            traffic[entry['revisionName']] = traffic.get(entry['revisionName'], 0) + entry['percent']
    if sum(traffic.values()) != 100: raise ValueError('Existing service must have 100% resolved traffic')
    return traffic

def normalize_traffic(entries):
    """Convert API status targets to exclusive, spec-safe traffic targets."""
    targets = []
    for entry in entries:
        if not isinstance(entry, dict): raise ValueError('Traffic target must be an object')
        for key in ['revisionName', 'revision_name']:
            if key in entry and not isinstance(entry[key], str): raise ValueError('Traffic revisionName must be a string')
        for key in ['latestRevision', 'latest_revision']:
            if key in entry and not isinstance(entry[key], bool): raise ValueError('Traffic latestRevision must be boolean')
        if 'percent' in entry and (type(entry['percent']) is not int or not 0 <= entry['percent'] <= 100):
            raise ValueError('Traffic percent must be an integer between 0 and 100')
        if entry.get('revisionName') and entry.get('revision_name') and entry['revisionName'] != entry['revision_name']:
            raise ValueError('Conflicting revisionName field styles')
        revision = entry.get('revisionName') or entry.get('revision_name')
        latest = entry.get('latestRevision', entry.get('latest_revision', False))
        target = {key: entry[key] for key in ['percent', 'tag'] if key in entry}
        if revision:
            target['revisionName'] = revision
        elif latest is True:
            target['latestRevision'] = True
        else:
            raise ValueError('Traffic target requires a revisionName or latestRevision=true')
        targets.append(target)
    return targets

def validate_manifest_traffic(manifest, preserved=None):
    """Reject ambiguous targets and production traffic to a new candidate."""
    entries = manifest.get('spec', {}).get('traffic', [])
    normalize_traffic(entries)
    for index, target in enumerate(entries):
        revision = target.get('revisionName') or target.get('revision_name')
        latest = target.get('latestRevision') is True or target.get('latest_revision') is True
        if revision and latest:
            raise ValueError(f'Invalid spec.traffic[{index}]: revisionName and latestRevision=true are mutually exclusive')
        if revision and ('latestRevision' in target or 'latest_revision' in target):
            raise ValueError(f'Invalid spec.traffic[{index}]: remove latestRevision from explicit revision target')
        if not revision and not latest:
            raise ValueError(f'Invalid spec.traffic[{index}]: missing revision target')
    if preserved is not None:
        candidate = manifest['spec']['template']['metadata']['name']
        if any(t.get('latestRevision') is True or t.get('latest_revision') is True for t in entries):
            raise ValueError('Candidate replacement requires explicit preserved revisions; latestRevision would route to the candidate')
        current = allocation({'status': {'traffic': entries}})
        if current != preserved or candidate in current:
            raise ValueError('Candidate replacement must preserve production allocation and give the candidate no production traffic')

def preserved_traffic(service):
    """Pin any unresolved latest target to the previously ready revision."""
    targets = normalize_traffic(service['status'].get('traffic', []))
    for target in targets:
        if target.pop('latestRevision', False):
            revision = service['status'].get('latestReadyRevisionName') or service['status'].get('latest_ready_revision_name')
            if not revision:
                raise ValueError('Cannot preserve latestRevision traffic: live latestReadyRevisionName is missing')
            target['revisionName'] = revision
    allocation({'status': {'traffic': targets}})
    return targets

def identity_token(config, service):
    # Generate a fresh token with the canonical audience, including for tagged revision URLs.
    try:
        return subprocess.check_output(['gcloud', 'auth', 'print-identity-token',
            '--impersonate-service-account=' + config['serviceAccounts'][service],
            '--audiences=' + config['urls'][service], '--include-email'], text=True).strip()
    except subprocess.CalledProcessError as error:
        raise ValueError('Private health checks require roles/iam.serviceAccountTokenCreator on ' + config['serviceAccounts'][service] + ' for the deployment identity') from error

def health(config, url, service):
    headers = {} if service in PUBLIC else {'X-Serverless-Authorization': 'Bearer ' + identity_token(config, service)}
    path = '/health' if service == 'frontend' else '/actuator/health/readiness'
    request = urllib.request.Request(url + path, headers=headers)
    for attempt in range(12):
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                body = response.read().decode()
                if response.status == 200 and (body.strip() == 'ok' if service == 'frontend' else json.loads(body).get('status') == 'UP'): return
        except (OSError, ValueError): pass
        if attempt < 11: time.sleep(5)
    raise RuntimeError('Readiness failed for ' + service)

def release(config, files, output):
    config = copy.deepcopy(config)
    # All eight services must exist; creation/IAM/storage are provisioned separately, never guessed here.
    before = {service: gcloud(config, 'run', 'services', 'describe', config['prefix'] + '-' + service) for service in SERVICES}
    warnings = []
    for service in SERVICES:
        actual = before[service]['status']['url']
        if actual != config['urls'][service]: warnings.append(service + ': using discovered canonical URL ' + actual)
        config['urls'][service] = actual
    # Private IAM boundaries are checked before any candidate changes.
    for service in SERVICES:
        policy = gcloud(config, 'run', 'services', 'get-iam-policy', config['prefix'] + '-' + service)
        members = {member for binding in policy.get('bindings', []) if binding.get('role') == 'roles/run.invoker'
                   for member in binding.get('members', [])}
        public = service in {'frontend', 'api-gateway'}
        disabled = str(before[service].get('metadata', {}).get('annotations', {}).get('run.googleapis.com/invoker-iam-disabled', 'false')).lower() == 'true'
        if disabled and not public: raise ValueError('Internal service must enforce invoker IAM: ' + service)
        if not public and ({'allUsers', 'allAuthenticatedUsers'} & members):
            raise ValueError('Backend service must not have public invoker access: ' + service)
        if public and not disabled and 'allUsers' not in members:
            raise ValueError('Public edge invoker policy is missing: ' + service)
        # Release checks impersonate the service runtime identity; it must invoke its own service.
        if 'serviceAccount:' + config['serviceAccounts'][service] not in members and not public:
            raise ValueError('Runtime self-invoker permission is required for health checks: ' + service)
        for caller, targets in CALLS.items():
            if service in targets and 'serviceAccount:' + config['serviceAccounts'][caller] not in members and not public:
                raise ValueError('Missing configured caller invoker permission: ' + caller + ' -> ' + service)
    targets = {service: preserved_traffic(before[service]) for service in SERVICES}
    previous = {service: allocation({'status': {'traffic': targets[service]}}) for service in SERVICES}
    output.parent.mkdir(parents=True, exist_ok=True)
    (output.parent / 'pre-release.json').write_text(json.dumps(previous, indent=2))
    promoted, candidates = [], {}
    tag = 'verify-' + os.environ['GITHUB_RUN_ID']
    try:
        for service in SERVICES:
            manifest = json.loads(files[service].read_text())
            # Rerender outbound audiences from discovered URLs, retaining the saved immutable images.
            old_template = manifest['spec']['template']
            containers = old_template['spec']['containers']
            suffix = old_template['metadata']['name'].removeprefix(config['prefix'] + '-' + service + '-')
            manifest = render(config, service, containers[0]['image'], containers[-1]['image'], suffix)
            if service in PUBLIC and str(before[service].get('metadata', {}).get('annotations', {}).get('run.googleapis.com/invoker-iam-disabled', 'false')).lower() == 'true':
                manifest['metadata']['annotations']['run.googleapis.com/invoker-iam-disabled'] = 'true'
            # Preserve traffic exactly, including tagged entries, while creating the candidate revision.
            manifest['spec']['traffic'] = targets[service]
            validate_manifest_traffic(manifest, previous[service])
            files[service].write_text(json.dumps(manifest, indent=2))
            gcloud(config, 'run', 'services', 'replace', str(files[service]))
            name = config['prefix'] + '-' + service
            revision = manifest['spec']['template']['metadata']['name']
            candidates[service] = revision
            gcloud(config, 'run', 'services', 'update-traffic', name, '--update-tags=' + tag + '=' + revision)
            ready = gcloud(config, 'run', 'services', 'describe', name)
            url = next(entry['url'] for entry in ready['status']['traffic'] if entry.get('tag') == tag)
            health(config, url, service)
            # Rollback tracking is set BEFORE the promotion API because a client timeout may follow a successful write.
            promoted.append(service)
            gcloud(config, 'run', 'services', 'update-traffic', name, '--to-revisions=' + revision + '=100')
            current = gcloud(config, 'run', 'services', 'describe', name)
            if allocation(current) != {revision: 100}: raise RuntimeError('Traffic did not converge: ' + service)
            health(config, config['urls'][service], service)
        output.write_text(json.dumps({'status': 'PASS', 'revisions': candidates, 'urls': config['urls'], 'warnings':warnings,
                                     'scope': 'readiness and traffic; business/telemetry cloud acceptance is separate'}, indent=2))
    except Exception:
        for service in reversed(promoted):
            split = ','.join(key + '=' + str(value) for key, value in previous[service].items())
            try: gcloud(config, 'run', 'services', 'update-traffic', config['prefix'] + '-' + service, '--to-revisions=' + split)
            except subprocess.CalledProcessError: print('::error::Rollback failed for ' + service + '; use saved pre-release.json')
        raise
    finally:
        for service in candidates:
            try: gcloud(config, 'run', 'services', 'update-traffic', config['prefix'] + '-' + service, '--remove-tags=' + tag)
            except subprocess.CalledProcessError: print('::warning::Remove temporary tag manually for ' + service)
        (output.parent / 'pre-release.json').write_text(json.dumps(previous, indent=2))

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['validate', 'render', 'deploy'])
    parser.add_argument('--config', required=True)
    parser.add_argument('--images')
    parser.add_argument('--revision', default='local-check')
    parser.add_argument('--out', default='artifacts/cloud-release')
    parser.add_argument('--allow-example', action='store_true')
    args = parser.parse_args()
    config = validate(json.loads(Path(args.config).read_text()), args.allow_example and args.command != 'deploy')
    if args.command == 'validate': print('Cloud configuration syntax PASS; IAM/storage/live URLs are not verified.'); raise SystemExit()
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,18}', args.revision): raise ValueError('Invalid revision suffix')
    images = json.loads(Path(args.images).read_text())
    out = Path(args.out); out.mkdir(parents=True, exist_ok=True)
    files = {}
    for service in SERVICES:
        manifest = render(config, service, images[service], images['collector'], args.revision)
        files[service] = out / (service + '.json'); files[service].write_text(json.dumps(manifest, indent=2))
    if args.command == 'deploy': release(config, files, out / 'release-results.json')
    else: print('Rendered eight service manifests; no cloud changes made.')
