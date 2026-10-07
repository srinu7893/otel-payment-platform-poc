import { spawnSync } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = fileURLToPath(new URL('../', import.meta.url));
process.chdir(root);
const project = process.env.COMPOSE_PROJECT_NAME || 'otel-demo';
const composeArgs = ['compose', '-p', project, '-f', 'docker-compose.yml', '-f', 'docker-compose.otel.yml'];
function docker(args) {
  const result = spawnSync('docker', args, { stdio: 'inherit', windowsHide: true });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`Docker command failed (${result.status}). Check Docker Desktop's Linux engine and WSL installation.`);
}
const compose = (...args) => docker([...composeArgs, ...args]);
async function request(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(20000) });
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
  const text = await response.text();
  try { return JSON.parse(text); } catch { return text; }
}
async function eventually(label, check, timeout = 180000) {
  const deadline = Date.now() + timeout;
  let last;
  do {
    try { const value = await check(); if (value) { console.log(`PASS: ${label}`); return value; } }
    catch (error) { last = error.message; }
    await new Promise(resolve => setTimeout(resolve, 3000));
  } while (Date.now() < deadline);
  throw new Error(`${label} timed out: ${last || 'condition not met'}`);
}
async function agent() {
  // Keep the release and checksum in sync with the existing Linux setup script.
  const setup = await readFile('scripts/setup-otel-agent.sh', 'utf8');
  const version = setup.match(/^version=([\d.]+)$/m)?.[1];
  const expected = setup.match(/^expected=([a-f0-9]{64})$/m)?.[1];
  assert(version && expected, 'Cannot read pinned Java agent version/checksum');
  const destination = 'observability/agent/opentelemetry-javaagent.jar';
  const digest = bytes => createHash('sha256').update(bytes).digest('hex');
  try {
    if (digest(await readFile(destination)) === expected) return console.log(`Verified existing OTel Java agent ${version}`);
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const response = await fetch(`https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${version}/opentelemetry-javaagent.jar`, { signal: AbortSignal.timeout(180000) });
  assert(response.ok, `Agent download failed: HTTP ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  assert.equal(digest(bytes), expected, 'Java agent SHA256 mismatch');
  await mkdir('observability/agent', { recursive: true });
  await writeFile(destination, bytes);
  console.log(`Downloaded and verified OTel Java agent ${version}`);
}
async function ready() {
  await Promise.all([8079, 8084, 8082, Number(process.env.POC_GATEWAY_PORT || 8081), 8080, 8083, 8088].map(port =>
    eventually(`Service :${port}`, async () => (await request(`http://localhost:${port}/actuator/health`)).status === 'UP')));
  await Promise.all([
    ['Frontend', 'http://localhost:3000'], ['Grafana', 'http://localhost:3001/api/health'],
    ['Tempo', 'http://localhost:3200/ready'], ['Loki', 'http://localhost:3100/ready'],
    ['Prometheus', 'http://localhost:9090/-/ready'], ['Collector', 'http://localhost:13133'],
  ].map(([name, url]) => eventually(name, async () => { await request(url); return true; })));
}
async function smoke() {
  await ready();
  const base = 'http://localhost:8088/api/v1';
  const login = await request(`${base}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'demo', password: 'demo123' }) });
  assert(login.accessToken, 'Login did not return a token');
  const trace = randomBytes(16).toString('hex');
  const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${login.accessToken}`, traceparent: `00-${trace}-${randomBytes(8).toString('hex')}-01`, 'X-Correlation-Id': `local-${trace}` };
  const payment = await request(`${base}/payments`, { method: 'POST', headers, body: JSON.stringify({ idempotencyKey: `local-${trace}`, accountNumber: 'ACC1001', merchant: 'Local OTel smoke', amount: 1 }) });
  assert.equal(payment.status, 'COMPLETED');
  await eventually('Payment notification delivered', async () => {
    const page = await request(`${base}/notifications?paymentId=${payment.paymentId}`, { headers: { Authorization: headers.Authorization } });
    return page.content.some(row => row.paymentId === payment.paymentId && row.status === 'SENT');
  });
  await eventually('Connected payment and asynchronous trace', async () => {
    const data = await request(`http://localhost:3200/api/traces/${trace}`);
    const batches = data.batches || data.resourceSpans || [];
    const services = new Set(batches.map(batch => batch.resource?.attributes?.find(a => a.key === 'service.name')?.value?.stringValue));
    assert(['api-gateway', 'payment-service', 'customer-service', 'gateway-service', 'mock-bank-service', 'notification-service'].every(s => services.has(s)), 'Missing service spans');
    const rows = batches.flatMap(b => (b.scopeSpans || b.instrumentationLibrarySpans || []).flatMap(s => s.spans || []));
    const ids = new Set(rows.map(s => s.spanId));
    assert(rows.some(s => s.name === 'outbox.publish'), 'Missing outbox span');
    const externalParent = headers.traceparent.split('-')[2];
    const parentHex = value => /^[a-f0-9]{16}$/i.test(value) ? value : Buffer.from(value,'base64').toString('hex');
    for (const row of rows) if (row.parentSpanId && parentHex(row.parentSpanId) !== externalParent) assert(ids.has(row.parentSpanId), 'Disconnected span');
    return true;
  });
  await eventually('Payment logs match the trace ID', async () => {
    const query = `{service_name="payment-service"} | trace_id="${trace}"`;
    const result = await request(`http://localhost:3100/loki/api/v1/query_range?${new URLSearchParams({ query, limit: '100' })}`);
    return result.data.result.length > 0;
  });
  await eventually('Five core Prometheus scrape targets healthy (Alertmanager checked separately)', async () => {
    const result = await request('http://localhost:9090/api/v1/targets');
    const jobs = new Set(result.data.activeTargets.filter(t => t.health === 'up').map(t => t.labels.job));
    return ['otel-applications', 'otel-collector-internal', 'rabbitmq', 'tempo', 'prometheus'].every(j => jobs.has(j));
  });
  await eventually('HTTP, JVM and business metrics exported', async () => {
    for (const query of ['http_server_request_duration_seconds_count', 'jvm_memory_used_bytes', 'poc_outbox_publish_attempts_total{outcome="success"}']) {
      const result = await request(`http://localhost:9090/api/v1/query?${new URLSearchParams({ query })}`);
      assert(result.status === 'success' && result.data.result.length, `No data: ${query}`);
    }
    return true;
  });
  await mkdir('artifacts', { recursive: true });
  const grafanaHeaders = { Authorization: `Basic ${Buffer.from(`admin:${process.env.GRAFANA_ADMIN_PASSWORD || 'otel-demo-admin'}`).toString('base64')}` };
  for (const uid of ['payment-poc','payment-business','telemetry-pipeline']) {
    const result = await request(`http://localhost:3001/api/dashboards/uid/${uid}`, { headers: grafanaHeaders });
    assert(result.dashboard.panels.length >= 6, `Dashboard ${uid} is missing panels`);
  }
  for (const uid of ['prometheus','loki']) {
    const result = await request(`http://localhost:3001/api/datasources/uid/${uid}/health`, { headers: grafanaHeaders });
    assert.equal(result.status, 'OK', `Grafana data source ${uid}`);
  }
  const proxiedTrace = await request(`http://localhost:3001/api/datasources/proxy/uid/tempo/api/traces/${trace}`, { headers: grafanaHeaders });
  assert(proxiedTrace.batches?.length || proxiedTrace.resourceSpans?.length, 'Grafana Tempo query returned no spans');
  console.log('PASS: Three Grafana dashboards and all data-source connections');
  await writeFile('artifacts/local-smoke.json', JSON.stringify({ status: 'PASS', checkedAt: new Date().toISOString(), paymentId: payment.paymentId, traceId: trace, correlationId: `local-${trace}` }, null, 2));
  console.log(`Payment: ${payment.paymentId}\nTrace ID: ${trace}\nEvidence: artifacts/local-smoke.json`);
}

try {
  switch (process.argv[2] || 'help') {
    case 'start':
      docker(['info', '--format', '{{.ServerVersion}}']);
      compose('config', '--quiet');
      await agent();
      docker(['run', '--rm', '--mount', `type=bind,source=${root},target=/workspace`, '--mount', `type=volume,source=${project}-maven-cache,target=/root/.m2`, '-w', '/workspace', 'maven:3.9.9-eclipse-temurin-21', 'mvn', '-B', 'package', '-DskipTests']);
      compose('up', '--build', '-d');
      await ready();
      console.log('Ready: http://localhost:3000 | Grafana: http://localhost:3001');
      break;
    case 'agent': await agent(); break;
    case 'check': await smoke(); break;
    case 'status': compose('ps'); break;
    case 'logs': compose('logs', '-f', '--tail=100', ...process.argv.slice(3)); break;
    case 'stop': compose('stop'); break;
    default: console.log('Usage: node scripts/local-poc.mjs start|check|status|logs [service...]|stop|agent');
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
