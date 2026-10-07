import { readdir, readFile, writeFile } from 'node:fs/promises';
const root='observability/grafana/dashboards';
const auth={Authorization:`Basic ${Buffer.from(`admin:${process.env.GRAFANA_ADMIN_PASSWORD||'otel-demo-admin'}`).toString('base64')}`};
const report={checkedAt:new Date().toISOString(),dashboards:[],missingMetrics:[],errors:[]};
const api=async(url,headers={})=>{const r=await fetch(url,{headers,signal:AbortSignal.timeout(20000)});if(!r.ok)throw new Error(`HTTP ${r.status}: ${url}`);return r.json();};
for(const file of (await readdir(root)).filter(f=>f.endsWith('.json'))){
 const dashboard=JSON.parse(await readFile(`${root}/${file}`,'utf8'));
 const item={uid:dashboard.uid,title:dashboard.title,panels:dashboard.panels.length,queries:[],provisioned:false};report.dashboards.push(item);
 try{const live=await api(`http://localhost:3001/api/dashboards/uid/${dashboard.uid}`,auth);item.provisioned=live.dashboard.panels.length===dashboard.panels.length;}catch(error){report.errors.push(error.message);}
 for(const panel of dashboard.panels){if(panel.datasource?.uid!=='prometheus')continue;
  for(const target of panel.targets||[]){
   const query=target.expr.replaceAll('$__rate_interval','5m').replaceAll('$__range','30m').replaceAll('$__interval','10s').replaceAll('$service','.+');
   try{const r=await api('http://localhost:9090/api/v1/query?'+new URLSearchParams({query}));const rows=r.data?.result||[];item.queries.push({panel:panel.title,status:r.status,series:rows.length,finite:rows.filter(x=>Number.isFinite(Number(x.value?.[1]))).length,query});if(r.status!=='success')report.errors.push(`${dashboard.uid}: ${r.error}`);}catch(error){report.errors.push(`${dashboard.uid}/${panel.title}: ${error.message}`);}
  }
 }
 console.log(`${item.uid}: provisioned=${item.provisioned}, queries=${item.queries.length}, empty=${item.queries.filter(q=>!q.series).length}, nonfinite=${item.queries.filter(q=>q.series&&!q.finite).length}`);
}
for(const name of ['poc_payment_records','poc_notification_records','poc_outbox_pending','poc_business_snapshot_healthy','poc_synthetic_journey_success']){const r=await api('http://localhost:9090/api/v1/query?'+new URLSearchParams({query:name}));if(!r.data?.result?.length)report.missingMetrics.push(name);}
report.targets=(await api('http://localhost:9090/api/v1/targets')).data.activeTargets.map(t=>({job:t.labels.job,health:t.health,lastError:t.lastError}));
await writeFile('artifacts/native-dashboard-review.json',JSON.stringify(report,null,2));
console.log(JSON.stringify({missingMetrics:report.missingMetrics,targets:report.targets,errors:report.errors},null,2));
if(report.errors.length||report.dashboards.some(d=>!d.provisioned))process.exitCode=1;
