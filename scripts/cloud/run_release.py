#!/usr/bin/env python3
"""Render/validate or release existing Cloud Run services; no implicit infrastructure creation."""
import argparse
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
CALLS = {'payment-service': ['gateway-service', 'customer-service'], 'gateway-service': ['mock-bank-service'],
         'api-gateway': SERVICES[:6]}
JWT = {'auth-service', 'payment-service', 'notification-service', 'api-gateway'}
SECRET_RE = r'[A-Za-z0-9_-]+'

def validate(config, allow_example=False):
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
        env.update({'SPRING_RABBITMQ_HOST': config['rabbitHost'], 'SPRING_RABBITMQ_PORT': '5671', 'SPRING_RABBITMQ_SSL_ENABLED': 'true'})
        for name, key in [('SPRING_RABBITMQ_USERNAME', 'username'), ('SPRING_RABBITMQ_PASSWORD', 'password')]:
            secrets.append(secret(config, name, config['rabbitSecrets'][key]))
    targets = CALLS.get(service, [])
    if targets:
        env.update({'CLOUD_RUN_IAM_ENABLED': 'true', 'CLOUD_RUN_AUDIENCES': ','.join(config['urls'][target] for target in targets)})
    if service == 'payment-service':
        env.update({'CLIENTS_GATEWAY_BASE_URL': config['urls']['gateway-service'], 'CLIENTS_CUSTOMER_BASE_URL': config['urls']['customer-service']})
    if service == 'gateway-service': env['CLIENTS_BANK_BASE_URL'] = config['urls']['mock-bank-service']
    if service == 'api-gateway':
        for key, target in [('AUTH', 'auth-service'), ('CUSTOMER', 'customer-service'), ('PAYMENT', 'payment-service'), ('NOTIFICATION', 'notification-service'), ('GATEWAY', 'gateway-service'), ('BANK', 'mock-bank-service')]:
            env[f'SERVICES_{key}_URL'] = config['urls'][target]
    if service == 'frontend':
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
    annotations = {'autoscaling.knative.dev/maxScale': '1', 'run.googleapis.com/cpu-throttling': 'false',
                   'run.googleapis.com/startup-cpu-boost': 'true', 'run.googleapis.com/execution-environment': 'gen2',
                   'run.googleapis.com/network-interfaces': json.dumps([{'network': config['network'], 'subnetwork': config['subnetwork']}]),
                   'run.googleapis.com/vpc-access-egress': 'private-ranges-only'}
    # minScale matters for the existing scheduler/consumer implementation. Revisions can overlap during a rollout.
    if service in {'payment-service', 'notification-service'}: annotations['autoscaling.knative.dev/minScale'] = '1'
    if service != 'frontend':
        annotations['run.googleapis.com/container-dependencies'] = json.dumps({'app': ['collector']})
        containers.append({'name': 'collector', 'image': collector, 'resources': {'limits': {'cpu': '1', 'memory': '256Mi'}},
                           'env': [{'name': 'GOOGLE_CLOUD_PROJECT', 'value': config['project']}],
                           'startupProbe': {'httpGet': {'path': '/', 'port': 13133}, 'periodSeconds': 3, 'timeoutSeconds': 3, 'failureThreshold': 20}})
    if config.get('grafana') and service != 'frontend':
        sidecar = containers[-1]
        sidecar['args'] = ['--config=/etc/otelcol-contrib/config.yaml', '--config=/etc/otelcol-contrib/grafana.yaml']
        sidecar['env'].extend([
            {'name': 'GRAFANA_OTLP_ENDPOINT', 'value': config['grafana']['endpoint']},
            {'name': 'GRAFANA_OTLP_USERNAME', 'value': config['grafana']['username']},
            secret(config, 'GRAFANA_OTLP_TOKEN', config['grafana']['tokenSecret'])])
    return {'apiVersion': 'serving.knative.dev/v1', 'kind': 'Service', 'metadata': {'name': config['prefix'] + '-' + service,
              'annotations': {'run.googleapis.com/ingress': 'all'}},
            'spec': {'template': {'metadata': {'name': config['prefix'] + '-' + service + '-' + revision,
                                'annotations': annotations}, 'spec': {'serviceAccountName': config['serviceAccounts'][service],
                                'containerConcurrency': 20, 'timeoutSeconds': 60, 'containers': containers}}}}

def gcloud(config, *args):
    output = subprocess.check_output(['gcloud', '--quiet', '--project=' + config['project'], '--format=json',
                                     *args, '--region=' + config['region']], text=True)
    return json.loads(output or 'null')

def allocation(service):
    traffic = {}
    for entry in service['status'].get('traffic', []):
        if entry.get('percent', 0):
            if not entry.get('revisionName'): raise ValueError('Unresolved current traffic')
            traffic[entry['revisionName']] = traffic.get(entry['revisionName'], 0) + entry['percent']
    if sum(traffic.values()) != 100: raise ValueError('Existing service must have 100% resolved traffic')
    return traffic

def health(config, url, service):
    # Generate a fresh token with the canonical audience, including for tagged revision URLs.
    token = subprocess.check_output(['gcloud', 'auth', 'print-identity-token',
        '--impersonate-service-account=' + config['serviceAccounts'][service],
        '--audiences=' + config['urls'][service], '--include-email'], text=True).strip()
    path = '/health' if service == 'frontend' else '/actuator/health/readiness'
    request = urllib.request.Request(url + path, headers={'X-Serverless-Authorization': 'Bearer ' + token})
    for attempt in range(12):
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                body = response.read().decode()
                if response.status == 200 and (body.strip() == 'ok' if service == 'frontend' else json.loads(body).get('status') == 'UP'): return
        except (OSError, ValueError): pass
        if attempt < 11: time.sleep(5)
    raise RuntimeError('Readiness failed for ' + service)

def release(config, files, output):
    # All eight services must exist; creation/IAM/storage are provisioned separately, never guessed here.
    before = {service: gcloud(config, 'run', 'services', 'describe', config['prefix'] + '-' + service) for service in SERVICES}
    for service in SERVICES:
        if before[service]['status']['url'] != config['urls'][service]: raise ValueError('Configured URL differs from live canonical URL: ' + service)
    # Private IAM boundaries are checked before any candidate changes.
    for service in SERVICES:
        policy = gcloud(config, 'run', 'services', 'get-iam-policy', config['prefix'] + '-' + service)
        members = {member for binding in policy.get('bindings', []) if binding.get('role') == 'roles/run.invoker'
                   for member in binding.get('members', [])}
        public = service in {'frontend', 'api-gateway'}
        if not public and ({'allUsers', 'allAuthenticatedUsers'} & members):
            raise ValueError('Backend service must not have public invoker access: ' + service)
        if public and 'allUsers' not in members:
            raise ValueError('Public edge invoker policy is missing: ' + service)
        # Release checks impersonate the service runtime identity; it must invoke its own service.
        if 'serviceAccount:' + config['serviceAccounts'][service] not in members and not public:
            raise ValueError('Runtime self-invoker permission is required for health checks: ' + service)
        for caller, targets in CALLS.items():
            if service in targets and 'serviceAccount:' + config['serviceAccounts'][caller] not in members and not public:
                raise ValueError('Missing configured caller invoker permission: ' + caller + ' -> ' + service)
    previous = {service: allocation(before[service]) for service in SERVICES}
    output.parent.mkdir(parents=True, exist_ok=True)
    (output.parent / 'pre-release.json').write_text(json.dumps(previous, indent=2))
    promoted, candidates = [], {}
    tag = 'verify-' + os.environ['GITHUB_RUN_ID']
    try:
        for service in SERVICES:
            manifest = json.loads(files[service].read_text())
            # Preserve traffic exactly, including tagged entries, while creating the candidate revision.
            manifest['spec']['traffic'] = before[service]['status'].get('traffic', [])
            # status fields must not be sent in a spec traffic target.
            for entry in manifest['spec']['traffic']: entry.pop('url', None)
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
        output.write_text(json.dumps({'status': 'PASS', 'revisions': candidates, 'urls': config['urls'],
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
