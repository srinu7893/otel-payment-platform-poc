import { useState } from 'react';

const CLOUD_PROJECT = import.meta.env.VITE_GOOGLE_CLOUD_PROJECT || '';
const CLOUD_NATIVE = Boolean(CLOUD_PROJECT) && !import.meta.env.VITE_GRAFANA_URL;
const cloudUrl = (path, query = '') => `https://console.cloud.google.com/${path}?${new URLSearchParams({ project: CLOUD_PROJECT, ...(query ? { query } : {}) })}`;
const GRAFANA = (import.meta.env.VITE_GRAFANA_URL || 'http://localhost:3001').replace(/\/$/, '');
function explore(uid, query) {
  return `${GRAFANA}/explore?${new URLSearchParams({ schemaVersion: '1', panes: JSON.stringify({
    poc: { datasource: uid, queries: [{ refId: 'A', datasource: { uid, type: uid }, ...query }],
      range: { from: 'now-1h', to: 'now' } }
  }) })}`;
}

export default function ObservabilityPanel() {
  const [value, setValue] = useState('');
  const [kind, setKind] = useState('correlation');
  const input = value.trim();
  const valid = kind === 'trace' ? /^[a-fA-F0-9]{32}$/.test(input) : input.length > 0 && input.length <= 100;
  const logQuery = `{service_name=~".+"} ${kind === 'trace' ? '| trace_id=' : '|='}${JSON.stringify(input)}`;
  return <section className="card">
    <h2>Observability</h2>
    <p>{CLOUD_NATIVE ? 'Inspect application logs, distributed traces and metrics in Google Cloud using your support account.' : 'Inspect traces, metrics and logs in Grafana using your observability account.'}</p>
    {CLOUD_NATIVE ? <p>
      <a href={cloudUrl('logs/query', `logName="projects/${CLOUD_PROJECT}/logs/payment-poc-otel"`)} target="_blank" rel="noreferrer">Application logs</a>
      {' · '}<a href={cloudUrl('traces')} target="_blank" rel="noreferrer">Distributed traces</a>
      {' · '}<a href={cloudUrl('monitoring/metrics-explorer')} target="_blank" rel="noreferrer">Cloud metrics</a>
    </p> : <p><a href={`${GRAFANA}/d/payment-poc`} target="_blank" rel="noreferrer">Operations dashboard</a>
      {' · '}<a href={explore('tempo', { queryType: 'traceql', query: '{ resource.service.name = "payment-service" }' })} target="_blank" rel="noreferrer">Payment traces and service map</a>
      {' · '}<a href={`${GRAFANA}/d/payment-business`} target="_blank" rel="noreferrer">Business outcomes</a>
      {' · '}<a href={`${GRAFANA}/d/telemetry-pipeline`} target="_blank" rel="noreferrer">Telemetry health</a>
      {' · '}<a href={`${GRAFANA}/d/payment-slo`} target="_blank" rel="noreferrer">Technical SLO dashboard</a></p>}
    {!CLOUD_PROJECT && <p><a href="http://localhost:9094" target="_blank" rel="noreferrer">Incident inbox</a>{' · '}<a href="http://localhost:9093" target="_blank" rel="noreferrer">Alert routing and silences</a></p>}
    {!CLOUD_NATIVE && <details><summary>Service metrics dashboards</summary><p><a href={`${GRAFANA}/d/platform-overview`} target="_blank" rel="noreferrer">Consolidated platform overview</a></p><p>{['api-gateway','auth-service','customer-service','payment-service','gateway-service','mock-bank-service','notification-service','frontend'].map(name => <a key={name} href={`${GRAFANA}/d/service-${name}`} target="_blank" rel="noreferrer" style={{marginRight:12}}>{name}</a>)}</p></details>}
    <div className="inlineForm">
      <label>Lookup<select value={kind} onChange={e => setKind(e.target.value)}><option value="correlation">Correlation ID</option><option value="trace">Trace ID</option></select></label>
      <label className="grow">ID<input value={value} maxLength={100} onChange={e => setValue(e.target.value)} placeholder={kind === 'trace' ? '32 hexadecimal characters' : 'X-Correlation-Id from the response'} /></label>
      {valid && <a href={CLOUD_NATIVE ? cloudUrl('logs/query', `logName="projects/${CLOUD_PROJECT}/logs/payment-poc-otel" AND ${kind === 'trace' ? `trace="projects/${CLOUD_PROJECT}/traces/${input.toLowerCase()}"` : `SEARCH(${JSON.stringify(input)})`}`) : explore('loki', { expr: logQuery, queryType: 'range' })} target="_blank" rel="noreferrer">Find logs</a>}
      {valid && kind === 'trace' && <a href={CLOUD_NATIVE ? cloudUrl('logs/query', `trace="projects/${CLOUD_PROJECT}/traces/${input.toLowerCase()}"`) : explore('tempo', { queryType: 'traceql', query: input.toLowerCase() })} target="_blank" rel="noreferrer">{CLOUD_NATIVE ? 'Find linked trace' : 'Open trace'}</a>}
    </div>
    <small>{CLOUD_NATIVE ? 'Set the time window in Cloud Logging, then follow the trace link on an event.' : 'Search covers the last hour. Increase the time range in Grafana for older requests.'}</small>
  </section>;
}
