const history = [];
const listeners = new Set();
export function recordDiagnostic(path, method, response, fallbackCorrelation) {
  // Route templates only: exclude query strings, credentials, bodies and concrete customer/transaction IDs.
  const operation = path.split('?')[0]
    .replace(/\/(payments|transfers|money-requests|customers)\/[^/]+/g, '/$1/{id}')
    .replace(/\/recipients\/[^/]+/g, '/recipients/{account}');
  const trace = response.headers.get('X-Trace-Id') || '';
  const correlation = response.headers.get('X-Correlation-Id') || fallbackCorrelation || '';
  history.unshift({ operation, method, status: response.status, time: new Date().toISOString(),
    traceId: /^[a-f0-9]{32}$/i.test(trace) && !/^0+$/.test(trace) ? trace.toLowerCase() : '',
    correlationId: /^[A-Za-z0-9._-]{1,100}$/.test(correlation) ? correlation : '' });
  history.splice(10);
  listeners.forEach(listener => listener([...history]));
}
export const recentDiagnostics = () => [...history];
export function subscribeDiagnostics(listener) { listeners.add(listener); return () => listeners.delete(listener); }
export function cloudEvidenceLinks(project, traceId, correlationId) {
  if (!/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(project)) return {};
  const validTrace = /^[a-f0-9]{32}$/i.test(traceId) && !/^0+$/.test(traceId);
  const query = validTrace ? `trace="projects/${project}/traces/${traceId.toLowerCase()}"`
    : /^[A-Za-z0-9._-]{1,100}$/.test(correlationId) ? `SEARCH("${correlationId}")` : '';
  return { logs: query ? `https://console.cloud.google.com/logs/query?${new URLSearchParams({ project, query })}` : '',
    trace: validTrace ? `https://console.cloud.google.com/traces/list?${new URLSearchParams({ project, tid: traceId.toLowerCase() })}` : '' };
}
