#!/usr/bin/env python3
"""Live acceptance: no synthetic backend fixtures, no third-party Python packages.
Run on a fresh local demo stack. Every result includes an actual assertion.
"""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = 'http://localhost:8088'
ARTIFACTS = Path('artifacts')
ARTIFACTS.mkdir(exist_ok=True)
RESULTS = []


def request(url, body=None, token=None, trace=None, expected=(200,)):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if trace:
        headers['traceparent'] = f'00-{trace}-1234567890abcdef-01'
        headers['X-Correlation-Id'] = 'e2e-' + trace
    req = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            code, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, raw = error.code, error.read()
    assert code in expected, f'{url}: HTTP {code}, expected {expected}'
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw.decode()


def eventually(fn, timeout=150):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            value = fn()
            if value:
                return value
        except (AssertionError, urllib.error.URLError, TimeoutError) as error:
            last = str(error)
        time.sleep(2)
    raise AssertionError(f'Condition not reached within {timeout}s; last error: {last}')


def case(name, fn):
    started = time.monotonic()
    try:
        fn()
        RESULTS.append({'scenario': name, 'status': 'PASS', 'seconds': round(time.monotonic()-started, 2)})
        print('PASS:', name, flush=True)
    except Exception as error:
        RESULTS.append({'scenario': name, 'status': 'FAIL', 'error': str(error)})
        raise
    finally:
        (ARTIFACTS/'otel-results.json').write_text(json.dumps(RESULTS, indent=2))


def login(user='demo', password='demo123'):
    return request(BASE+'/api/v1/auth/login', {'username':user, 'password':password})['accessToken']


def payment(token, trace=None):
    result = request(BASE+'/api/v1/payments', {'idempotencyKey': 'otel-'+uuid.uuid4().hex,
        'accountNumber':'ACC1001','merchant':'OTel Acceptance','amount':1}, token, trace, (200,201))
    assert result['status'] == 'COMPLETED', result['status']
    return result['paymentId']


def notification(token, payment_id):
    page = request(BASE+'/api/v1/notifications?paymentId='+payment_id, token=token)
    return any(x.get('paymentId') == payment_id and x.get('status') == 'SENT' for x in page['content'])


def spans(trace):
    data = request('http://localhost:3200/api/traces/'+trace)
    result = []
    for batch in data.get('batches', data.get('resourceSpans', [])):
        resource = {a['key']:a['value'].get('stringValue') for a in batch.get('resource',{}).get('attributes',[])}
        for scope in batch.get('scopeSpans', batch.get('instrumentationLibrarySpans', [])):
            for span in scope.get('spans', []):
                result.append((resource.get('service.name'), span))
    return result


def trace_complete(trace):
    rows = spans(trace)
    expected = {'api-gateway','payment-service','customer-service','gateway-service','mock-bank-service','notification-service'}
    assert expected <= {s for s,_ in rows}, 'Missing service spans: '+str(expected-{s for s,_ in rows})
    assert any(s['name']=='outbox.publish' for _,s in rows), 'Missing durable outbox span'
    assert any(any(a['key'] in ('db.system','db.system.name') for a in s.get('attributes',[])) for _,s in rows), 'Missing DB instrumentation'
    # Verify parent references are connected to this request, not just coincident trace IDs.
    ids = {s['spanId'] for _,s in rows}
    for service, span in rows:
        parent = span.get('parentSpanId')
        if service != 'api-gateway':
            assert parent in ids, f'Disconnected {service} span {span["name"]}'
    (ARTIFACTS/'trace-evidence.json').write_text(json.dumps({'traceId':trace,'services':sorted(expected),'spans':len(rows)},indent=2))
    return True


def prom(query):
    data = request('http://localhost:9090/api/v1/query?'+urllib.parse.urlencode({'query':query}))
    assert data['status'] == 'success'
    return data['data']['result']


def metrics_present():
    services = {r['metric']['service_name'] for r in prom('http_server_request_duration_seconds_count')}
    required={'api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service'}
    assert required <= services, 'Missing HTTP metrics: '+str(required-services)
    assert prom('jvm_memory_used_bytes'), 'Missing JVM metrics'
    assert prom('poc_outbox_publish_attempts_total{outcome="success"}'), 'Missing custom outbox metrics'
    assert prom('traces_service_graph_request_total'), 'Missing service graph metrics'
    return True


def logs_present(trace):
    query = '{service_name="payment-service"} | trace_id="'+trace+'"'
    data = request('http://localhost:3100/loki/api/v1/query_range?'+urllib.parse.urlencode({'query':query,'limit':100}))
    assert data['status']=='success'
    assert data['data']['result'], 'No logs correlated to payment trace'
    return True


def compose(*args):
    subprocess.run(['bash','scripts/otel-compose.sh',*args],check=True,timeout=120)


def main():
    token=login()
    case('Unauthenticated request is rejected', lambda: request(BASE+'/api/v1/payments', expected=(401,)))
    case('Customer cannot access support operations', lambda: request(BASE+'/api/v1/ops/health',token=token,expected=(403,)))
    case('Invalid credentials rejected',lambda: request(BASE+'/api/v1/auth/login',{'username':'demo','password':'wrong'},expected=(401,)))
    case('Source-account ownership enforced',lambda: request(BASE+'/api/v1/payments',{'idempotencyKey':uuid.uuid4().hex,'accountNumber':'ACC2001','merchant':'Ownership test','amount':1},token,expected=(403,)))
    trace=uuid.uuid4().hex
    payment_id=payment(token,trace)
    case('Outbox to RabbitMQ to notification delivered',lambda: eventually(lambda:notification(token,payment_id)))
    case('HTTP, JDBC and asynchronous spans share a connected trace',lambda:eventually(lambda:trace_complete(trace)))
    case('All service HTTP metrics, JVM, business metric and service graph exported',lambda:eventually(metrics_present))
    case('Payment logs correlated to exact trace ID',lambda:eventually(lambda:logs_present(trace)))
    case('All dashboards and Grafana datasource connections available',lambda: eventually(lambda: (dashboard_present() or True)))
    if os.environ.get('RESILIENCE','false').lower()=='true':
        def broker_recovery():
            compose('stop','rabbitmq')
            try:
                pid=payment(token)
                assert not notification(token,pid), 'Notification unexpectedly delivered while broker stopped'
                eventually(lambda: bool(prom('poc_outbox_publish_attempts_total{outcome="failure"}')),90)
            finally:
                compose('start','rabbitmq')
            eventually(lambda:notification(token,pid),180)
            eventually(lambda: bool(prom('poc_outbox_publish_attempts_total{outcome="failure"}')),90)
        case('Broker outage: business commit survives and outbox catches up',broker_recovery)
        def collector_recovery():
            compose('stop','otel-collector')
            try:
                payment(token)
            finally:
                compose('start','otel-collector')
            recovered_trace=uuid.uuid4().hex
            pid=payment(token,recovered_trace)
            eventually(lambda:notification(token,pid))
            eventually(lambda:trace_complete(recovered_trace))
        case('Collector outage: payment remains available; fresh traces recover',collector_recovery)
    case('Slow bank: unknown outcome and downstream error trace', lambda: fault_trace('slowdemo','slowdemo123','ACC-SLOW'))
    case('Bank 500: unknown outcome and downstream error trace', lambda: fault_trace('errordemo','errordemo123','ACC-ERROR'))


def fault_trace(user, password, account):
    trace = uuid.uuid4().hex
    token = login(user, password)
    result = request(BASE+'/api/v1/payments', {'idempotencyKey':'fault-'+uuid.uuid4().hex,
        'accountNumber':account,'merchant':'OTel Failure Acceptance','amount':1},token,trace,(200,201))
    assert result['status']=='RECONCILIATION_REQUIRED', result['status']
    def error_visible():
        rows=spans(trace)
        assert {'gateway-service','mock-bank-service'} <= {service for service,_ in rows}, 'Missing bank path in failure trace'
        assert any(span.get('status',{}).get('code') in (2,'STATUS_CODE_ERROR') for _,span in rows), 'No error span in failure trace'
        return True
    eventually(error_visible)


def dashboard_present():
    import base64
    password=os.environ.get('GRAFANA_ADMIN_PASSWORD','otel-demo-admin')
    headers={'Authorization':'Basic '+base64.b64encode(('admin:'+password).encode()).decode()}
    for uid in ('payment-poc','payment-business','telemetry-pipeline'):
        req=urllib.request.Request('http://localhost:3001/api/dashboards/uid/'+uid,headers=headers)
        with urllib.request.urlopen(req,timeout=15) as response:
            data=json.load(response)
        assert len(data['dashboard']['panels'])>=6, uid+' panels missing'
    for uid in ('prometheus','tempo','loki'):
        req=urllib.request.Request('http://localhost:3001/api/datasources/uid/'+uid+'/health',headers=headers)
        with urllib.request.urlopen(req,timeout=15) as response:
            data=json.load(response)
        assert data.get('status')=='OK', uid+' datasource unhealthy'


if __name__=='__main__':
    main()
