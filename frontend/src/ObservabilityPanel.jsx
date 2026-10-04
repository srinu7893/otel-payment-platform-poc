import { useState } from 'react';

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
    <p>Start the OTel stack to inspect traces, metrics and logs. Grafana uses a separate demo login.</p>
    <p><a href={`${GRAFANA}/d/payment-poc`} target="_blank" rel="noreferrer">Operations dashboard</a>
      {' · '}<a href={explore('tempo', { queryType: 'traceql', query: '{ resource.service.name = "payment-service" }' })} target="_blank" rel="noreferrer">Payment traces and service map</a></p>
    <div className="inlineForm">
      <label>Lookup<select value={kind} onChange={e => setKind(e.target.value)}><option value="correlation">Correlation ID</option><option value="trace">Trace ID</option></select></label>
      <label className="grow">ID<input value={value} maxLength={100} onChange={e => setValue(e.target.value)} placeholder={kind === 'trace' ? '32 hexadecimal characters' : 'X-Correlation-Id from the response'} /></label>
      {valid && <a href={explore('loki', { expr: logQuery, queryType: 'range' })} target="_blank" rel="noreferrer">Find logs</a>}
      {valid && kind === 'trace' && <a href={explore('tempo', { queryType: 'traceql', query: input.toLowerCase() })} target="_blank" rel="noreferrer">Open trace</a>}
    </div>
    <small>Search covers the last hour. Increase the time range in Grafana for older requests.</small>
  </section>;
}
