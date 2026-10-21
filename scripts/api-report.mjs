const secretKey=/^(password|confirmPassword|accessToken|refreshToken|token|authorization|x-serverless-authorization|cookie|set-cookie|api[-_]?key|client[-_]?secret)$/i;
export function scrub(value) {
  return String(value).replace(/Bearer\s+[^\s"'<>]+/gi,'Bearer [REDACTED]')
    .replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED JWT]')
    .replace(/(["'](?:password|access_?token|refresh_?token|api_key)["']\s*:\s*)("[^"]*"|'[^']*')/gi,'$1"[REDACTED]"')
    .replace(/((?:password|access_token|refresh_token|api_key)\s*[=:]\s*)[^\s,;&]+/gi,'$1[REDACTED]');
}
export function sanitize(value,depth=0) {
  if(depth>10)return '[depth limit]';
  if(Array.isArray(value))return [...value.slice(0,10).map(v=>sanitize(v,depth+1)),...(value.length>10?['[remaining items omitted]']:[])];
  if(value && typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,secretKey.test(k)?'[REDACTED]':sanitize(v,depth+1)]));
  return typeof value==='string'?scrub(value).slice(0,6000):value;
}
export function payload(text) {try{return sanitize(JSON.parse(text));}catch{return scrub(text).slice(0,6000);}}
export function uniqueExecutions(executions){return [...new Map(executions.map(e=>[e.cursor.ref,e])).values()];}
export function links(trace,project='project-c9bd3d0e-266f-47bf-852') {
  if(!/^[a-f0-9]{32}$/.test(trace || '') || /^0+$/.test(trace))return {};
  return {trace:'https://console.cloud.google.com/traces/list?'+new URLSearchParams({project,tid:trace}),
    logs:'https://console.cloud.google.com/logs/query?'+new URLSearchParams({project,query:`trace="projects/${project}/traces/${trace}"`})};
}
export const html=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function junit(suite){
  const cases=suite.requests.flatMap(row=>row.assertions.map(a=>({name:row.name+' / '+a.name,failed:!a.passed,message:a.error||''})))
    .concat(suite.failures.map(f=>({name:f.name||f.where||'Execution',failed:true,message:f.message})));
  return `<?xml version="1.0" encoding="UTF-8"?><testsuite name="${html(suite.name)}" tests="${cases.length}" failures="${cases.filter(c=>c.failed).length}">`+cases.map(c=>`<testcase name="${html(c.name)}">${c.failed?'<failure message="'+html(c.message)+'"/>':''}</testcase>`).join('')+'</testsuite>';
}
export function curl(record) {
  const command=[`curl.exe --request ${record.method} "${record.url}"`];
  if(record.authenticated)command.push('  --header "Authorization: Bearer %PAYMENT_JWT%"');
  for(const [key,value] of Object.entries(record.requestHeaders||{}))command.push(`  --header "${key}: ${value}"`);
  if(record.requestBody!==null)command.push(`  --data-binary "@${record.bodyFile||'request-body.json'}"`);
  return command.join(' ^\n');
}
export function markdown(report) {
  let text=`# API test execution: ${report.status}\n\nStarted: ${report.startedAt}. Source: ${report.source}. Uncommitted tracked changes: ${Boolean(report.workingTreeModified)}.\n\nPasswords, tokens and auth headers are redacted. Responses are actual observations, bounded to ten array items / 6,000 characters per string. Expected negative-test HTTP statuses are judged by assertions. Trace links do not themselves prove stored log/trace availability.\n\n`;
  text+='| Collection | Main requests | Network calls incl. login helpers | Assertions | Result |\n| --- | ---: | ---: | ---: | --- |\n';
  for(const suite of report.suites)text+=`| ${suite.name} | ${suite.requests.length}/${suite.expectedRequests} | ${suite.networkCalls} | ${suite.assertions} | ${suite.status} |\n`;
  for(const suite of report.suites){
    text+=`\n## ${suite.name}\n\n`;
    for(const [index,row] of suite.requests.entries()){
      text+=`### ${index+1}. ${row.name}\n\n${row.result} — ${row.method} ${row.url} — HTTP ${row.status} — ${row.durationMs} ms\n\n`;
      text+=`Request headers: Authorization ${row.authenticated?'present (redacted)':'absent'}; correlation ${row.correlationId || 'not reported'}; trace ${row.traceId || 'not reported'}.\n\n`;
      if(row.requestBody!==null)text+='Request body:\n```json\n'+JSON.stringify(row.requestBody,null,2)+'\n```\n\n';
      text+='Observed response:\n```json\n'+JSON.stringify(row.responseBody,null,2)+'\n```\n\n';
      text+='Assertions: '+row.assertions.map(a=>`${a.passed?'PASS':'FAIL'} ${a.name}`).join('; ')+'.\n\n';
      if(row.links.logs)text+=`[Correlated API logs](${row.links.logs}) · [Distributed trace](${row.links.trace})\n\n`;
      text+='Audit curl (run from this report directory; body files are saved automatically but credentials remain redacted. Use a fresh JWT in PAYMENT_JWT for protected calls. Ready-to-use sandbox login bodies are in docs/api/bodies):\n```cmd\n'+curl(row)+'\n```\n\n';
    }
    if(suite.failures.length)text+='Failures:\n```json\n'+JSON.stringify(suite.failures,null,2)+'\n```\n';
  }
  return text;
}
export function document(report) {
  let body=`<h1>API execution: ${html(report.status)}</h1><p>${html(report.startedAt)} · source ${html(report.source)}</p><p>Actual sanitized request/response evidence. Expected negative tests can pass with 4xx. Expand each row for bodies, assertions, curl and log/trace links.</p><input id="filter" placeholder="Filter by name, route, status or service" aria-label="Filter requests"><p><a href="api-tested.md">Markdown documentation</a> · <a href="results.json">Sanitized JSON evidence</a></p>`;
  for(const suite of report.suites){
    body+=`<h2>${html(suite.name)} — ${html(suite.status)}</h2><p>${suite.requests.length}/${suite.expectedRequests} main requests; ${suite.networkCalls} network calls including login helpers; ${suite.assertions} assertions.</p>`;
    for(const row of suite.requests){
      body+=`<details data-search="${html([row.name,row.method,row.url,row.status,row.result].join(' ').toLowerCase())}"><summary>${html(row.result)} · ${html(row.name)} · HTTP ${row.status} · ${row.durationMs} ms</summary><p>${html(row.method)} ${html(row.url)}</p>`;
      body+=`<p>Correlation: ${html(row.correlationId)}<br>Trace: ${html(row.traceId)}</p>`;
      if(row.links.logs)body+=`<p><a target="_blank" rel="noopener noreferrer" href="${html(row.links.logs)}">API logs</a> · <a target="_blank" rel="noopener noreferrer" href="${html(row.links.trace)}">Trace waterfall</a></p>`;
      body+='<h3>Request body</h3><pre>'+html(JSON.stringify(row.requestBody,null,2))+'</pre><h3>Observed response</h3><pre>'+html(JSON.stringify(row.responseBody,null,2))+'</pre><h3>Assertions</h3><ul>';
      for(const assertion of row.assertions)body+=`<li>${assertion.passed?'PASS':'FAIL'} ${html(assertion.name)}</li>`;
      body+='</ul><h3>CMD curl</h3><pre>'+html(curl(row))+'</pre></details>';
    }
    if(suite.failures.length)body+='<pre>'+html(JSON.stringify(suite.failures,null,2))+'</pre>';
  }
  return '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Payment API test results</title><style>body{font:16px system-ui;max-width:1100px;margin:30px auto;padding:0 18px;color:#17243a;background:#f4f7fb}details{background:white;border:1px solid #ccd6e2;border-radius:8px;padding:14px;margin:10px 0}summary{cursor:pointer;font-weight:600}pre{background:#102035;color:#e8f2ff;padding:16px;overflow:auto;border-radius:6px;white-space:pre-wrap;overflow-wrap:anywhere}a{color:#1263a0}input{padding:12px;width:min(95%,650px)}h2{margin-top:32px}</style>'+body+'<script>document.querySelector("#filter").addEventListener("input",e=>document.querySelectorAll("details").forEach(d=>d.hidden=!d.dataset.search.includes(e.target.value.toLowerCase())))</script></html>';
}
