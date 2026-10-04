import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import path from 'node:path';

const outDir = path.resolve('../docs/demo/screenshots');
mkdirSync(outDir, { recursive: true });

const wait = ms => new Promise(r => setTimeout(r, ms));

async function login(page, username, password) {
  await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('heading', { name: 'Payment Platform' }).waitFor();
  await wait(1200);
}

function traceDurationMicros(trace) {
  const spans = trace?.spans || [];
  if (!spans.length) return 0;
  const start = Math.min(...spans.map(s => Number(s.startTime || 0)));
  const end = Math.max(...spans.map(s => Number(s.startTime || 0) + Number(s.duration || 0)));
  return Math.max(0, end - start);
}

async function getDemoTraceIds() {
  for (let attempt = 0; attempt < 20; attempt++) {
    const res = await fetch('http://localhost:16686/api/traces?service=api-gateway&limit=50&lookback=1h');
    if (res.ok) {
      const body = await res.json();
      const traces = body?.data || [];
      const paymentTraces = traces.filter(t => {
        const json = JSON.stringify(t);
        return json.includes('/api/v1/payments') || json.includes('POST /api/v1/payments');
      });
      const candidates = paymentTraces.length ? paymentTraces : traces;
      if (candidates.length) {
        const sorted = [...candidates].sort((a, b) => traceDurationMicros(a) - traceDurationMicros(b));
        return {
          normalTraceId: sorted[0].traceID,
          slowTraceId: sorted[sorted.length - 1].traceID,
          slowDurationMicros: traceDurationMicros(sorted[sorted.length - 1])
        };
      }
    }
    await wait(1500);
  }
  throw new Error('No Jaeger trace available');
}

const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });

await login(page, 'demo', 'demo123');
await page.screenshot({ path: path.join(outDir, '01-customer-overview.png'), fullPage: true });

await page.getByRole('button', { name: 'notifications' }).click();
await wait(800);
await page.screenshot({ path: path.join(outDir, '02-customer-notifications.png'), fullPage: true });

await page.getByRole('button', { name: 'Sign out' }).click();
await login(page, 'support', 'support123');
await page.getByRole('heading', { name: 'Support / Admin dashboard' }).waitFor();
await wait(1500);
await page.screenshot({ path: path.join(outDir, '03-support-observability-dashboard.png'), fullPage: true });

const traces = await getDemoTraceIds();
await page.goto('http://localhost:16686/trace/' + traces.normalTraceId, { waitUntil: 'networkidle' });
await wait(2500);
await page.screenshot({ path: path.join(outDir, '04-jaeger-normal-payment-trace.png'), fullPage: true });

await page.goto('http://localhost:16686/trace/' + traces.slowTraceId, { waitUntil: 'networkidle' });
await wait(2500);
await page.screenshot({ path: path.join(outDir, '05-jaeger-slow-failure-trace.png'), fullPage: true });

const grafanaLogin = await page.request.post('http://localhost:3001/login', {
  data: { user: 'admin', password: 'admin' }
});
if (!grafanaLogin.ok()) {
  throw new Error('Grafana API login failed with HTTP ' + grafanaLogin.status());
}
await page.goto('http://localhost:3001/d/payment-platform-overview/payment-platform-otel-overview?orgId=1&from=now-15m&to=now', { waitUntil: 'networkidle' });
await wait(3500);
await page.screenshot({ path: path.join(outDir, '06-grafana-payment-platform-overview.png'), fullPage: true });

await page.goto('http://localhost:9090/targets', { waitUntil: 'networkidle' });
await wait(1000);
await page.screenshot({ path: path.join(outDir, '07-prometheus-targets.png'), fullPage: true });

let logs = '';
try {
  logs = execSync(
    'docker compose logs --no-color api-gateway payment-service gateway-service mock-bank-service notification-service',
    { cwd: path.resolve('..'), encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 }
  )
    .split('\n')
    .filter(line => line.includes('"trace_id"') || line.includes('RECONCILIATION_REQUIRED') || line.includes('OUTBOX_PUBLISHED'))
    .slice(-80)
    .join('\n');
} catch (e) {
  logs = 'Unable to collect docker logs: ' + e.message;
}
await page.setContent(`
<!doctype html>
<html>
<head><meta charset="utf-8"><style>
body { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; background:#0b1020; color:#d9e2ff; padding:24px; }
h1 { font-family: system-ui, sans-serif; color:white; }
p { color:#a9b5d1; }
pre { white-space:pre-wrap; word-break:break-word; font-size:13px; line-height:1.45; }
.trace { color:#7dd3fc; }
</style></head>
<body>
<h1>Trace / Log Correlation</h1>
<p>Real structured logs from the running payment platform. Look for correlationId, trace_id, span_id and business events.</p>
<pre>${logs.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;')}</pre>
</body>
</html>`);
await page.screenshot({ path: path.join(outDir, '08-trace-log-correlation.png'), fullPage: true });

const md = `# Live Observability Demo Screenshots

Generated automatically from the running Docker stack after the end-to-end CI scenarios pass.

1. Customer overview — \`01-customer-overview.png\`
2. RabbitMQ-driven notification activity — \`02-customer-notifications.png\`
3. Support/Admin observability + service health — \`03-support-observability-dashboard.png\`
4. Jaeger normal payment distributed trace — \`04-jaeger-normal-payment-trace.png\`
5. Jaeger slow/timeout trace — \`05-jaeger-slow-failure-trace.png\`
6. Grafana payment platform dashboard — \`06-grafana-payment-platform-overview.png\`
7. Prometheus targets — \`07-prometheus-targets.png\`
8. Structured trace/log correlation — \`08-trace-log-correlation.png\`

Normal Jaeger trace: \`${traces.normalTraceId}\`
Slow/failure Jaeger trace: \`${traces.slowTraceId}\` (~${Math.round(traces.slowDurationMicros / 1000)} ms)
`;
writeFileSync(path.resolve('../docs/demo/SCREENSHOTS.md'), md);

await browser.close();
console.log('Captured demo screenshots to', outDir);
