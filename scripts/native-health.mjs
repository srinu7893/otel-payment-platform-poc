import { readFile, writeFile } from 'node:fs/promises';
const get = async (url, options={}) => { const r=await fetch(url,{...options,signal:AbortSignal.timeout(15000)}); if(!r.ok)throw new Error(`${url}: ${r.status}`); return r.json(); };
const targets = (await get('http://localhost:9090/api/v1/targets')).data.activeTargets.map(t=>({job:t.labels.job,health:t.health,lastError:t.lastError}));
const alerts = (await get('http://localhost:9090/api/v1/alerts')).data.alerts.map(a=>({name:a.labels.alertname,state:a.state,service:a.labels.service,description:a.annotations.description}));
const login = await get('http://localhost:8088/api/v1/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'support',password:'support123'})});
const operations = await get('http://localhost:8088/api/v1/ops/health',{headers:{Authorization:`Bearer ${login.accessToken}`}});
const recentErrors = [];
for (const service of ['auth-service','customer-service','mock-bank-service','gateway-service','notification-service','payment-service','api-gateway']) {
 const lines=(await readFile(`artifacts/native-runtime/logs/${service}.log`,'utf8')).split(/\r?\n/);
 for(const line of lines) { try {const row=JSON.parse(line); if(row.level==='ERROR'&&Date.parse(row['@timestamp'])>Date.now()-180000)recentErrors.push({service,time:row['@timestamp'],message:row.message});}catch{} }
}
const result={checkedAt:new Date().toISOString(),targets,alerts,operations,recentErrors:recentErrors.slice(-10)};
await writeFile('artifacts/native-health.json',JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
