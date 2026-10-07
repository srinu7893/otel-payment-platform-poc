import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
const get=async url=>{const r=await fetch(url);assert(r.ok,`${url}: ${r.status}`);return r.json();};
const prom=async query=>(await get('http://127.0.0.1:9090/api/v1/query?'+new URLSearchParams({query}))).data.result;
const probes=await prom('poc_readiness_probe_success');assert.equal(probes.length,8);assert(probes.every(r=>Number(r.value[1])===1));
assert((await prom('poc_scheduled_journey_success')).some(r=>r.value[1]==='1'));
const targets=(await get('http://127.0.0.1:9090/api/v1/targets')).data.activeTargets;assert(targets.every(t=>t.health==='up'));
for(const [body,origin,status] of [[{name:'URL',value:1},'http://localhost:3000',400],[{name:'LCP',value:1,token:'not-a-real-secret'},'http://localhost:3000',400],[{name:'LCP',value:1},'http://untrusted.invalid',403]]){
 const r=await fetch('http://127.0.0.1:9097/rum',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify(body)});assert.equal(r.status,status);
}
const browser=await prom('poc_browser_observation_count');assert(browser.some(r=>r.metric.metric==='PAGE_VIEW'));assert(browser.some(r=>r.metric.metric==='FCP'));
const report={checkedAt:new Date().toISOString(),status:'PASS',readiness:probes.map(r=>({service:r.metric.service_name,ready:Number(r.value[1])})),targets:targets.map(t=>({job:t.labels.job,health:t.health})),browserMetrics:browser.map(r=>r.metric.metric),checks:['8 readiness probes healthy','scheduled journey passed','all scrape targets UP','unknown metric and extra fields rejected','foreign browser origin rejected','real browser page view and FCP exported']};
await writeFile('artifacts/native-additions.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
