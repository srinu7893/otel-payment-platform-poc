import http from 'node:http';
import os from 'node:os';
import {spawn} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));process.chdir(root);
const ports={'api-gateway':8088,'auth-service':8079,'customer-service':8084,'payment-service':8080,'gateway-service':18081,'mock-bank-service':8082,'notification-service':8083};
const targets=[...Object.entries(ports).map(([service,port])=>({service,url:`http://127.0.0.1:${port}/actuator/health`,json:true})),{service:'frontend',url:'http://127.0.0.1:3000'}];
const state=new Map();let synthetic={};let busy=false;
const rum=new Map();const allowedRum=new Set(['CLS','FCP','INP','LCP','TTFB','PAGE_VIEW','JS_ERROR']);
let database={},cpuPrevious=os.cpus(),cpuRatio=0;
async function databaseProbe(){
 const executable=path.join(process.env.PG_HOME||'C:/Program Files/PostgreSQL/17','bin/psql.exe');
 const sql="select row_to_json(s) from (select pg_database_size('payments') as size_bytes, (select count(*) from pg_stat_activity where datname='payments') as connections, (select count(*) from pg_stat_activity where datname='payments' and wait_event_type='Lock') as lock_waiters, xact_commit as commits_total, xact_rollback as rollbacks_total, deadlocks as deadlocks_total from pg_stat_database where datname='payments') s";
 const child=spawn(executable,['-h','127.0.0.1','-p','55432','-U','payments','-d','postgres','-tAc',sql],{windowsHide:true,env:{...process.env,PGPASSWORD:'payments',PGCONNECT_TIMEOUT:'3',PGOPTIONS:'-c statement_timeout=2000'},stdio:['ignore','pipe','ignore']});
 let raw='';child.stdout.on('data',b=>{raw+=b;if(raw.length>10000)child.kill();});const timer=setTimeout(()=>child.kill(),5000);let finished=false;
 const done=code=>{if(finished)return;finished=true;clearTimeout(timer);try{if(code!==0)throw Error();database={...JSON.parse(raw),healthy:1,timestamp:Date.now()/1000};}catch{database.healthy=0;}setTimeout(databaseProbe,30000);};child.once('error',()=>done(1));child.once('exit',done);
}
let rumWindow=Date.now(),rumRequests=0;
async function ingestRum(req,res){
 if(!['http://localhost:3000','http://127.0.0.1:3000'].includes(req.headers.origin)){res.writeHead(403);res.end();return;}
 if(Date.now()-rumWindow>60000){rumWindow=Date.now();rumRequests=0;}
 if(++rumRequests>120){res.writeHead(429);res.end();return;}
 try{let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>256){res.writeHead(413);res.end();return;}}
  const data=JSON.parse(raw);if(Object.keys(data).sort().join(',')!=='name,value'||!allowedRum.has(data.name)||!Number.isFinite(data.value)||data.value<0||data.value>600000)throw Error('Invalid');
  const previous=rum.get(data.name)||{sum:0,count:0};rum.set(data.name,{sum:previous.sum+data.value,count:previous.count+1,last:data.value,timestamp:Date.now()/1000});res.writeHead(204);res.end();
 }catch{res.writeHead(400);res.end();}
}
async function probe(target){const start=performance.now();let success=0;
 try{const r=await fetch(target.url,{signal:AbortSignal.timeout(5000)});success=Number(r.ok&&(!target.json||(await r.json()).status==='UP'));}catch{}
 const previous=state.get(target.service)||{total:0,passed:0};state.set(target.service,{success,duration:(performance.now()-start)/1000,timestamp:Date.now()/1000,total:previous.total+1,passed:previous.passed+success});
}
async function cycle(){await Promise.all(targets.map(probe));const current=os.cpus();let idle=0,total=0;current.forEach((c,i)=>{if(!cpuPrevious[i])return;for(const k of Object.keys(c.times)){const delta=c.times[k]-cpuPrevious[i].times[k];total+=delta;if(k==='idle')idle+=delta;}});if(total>0)cpuRatio=1-idle/total;cpuPrevious=current;setTimeout(cycle,10000);}
async function journey(){if(busy)return;busy=true;
 const child=spawn(path.join(root,'artifacts/native/python/python.exe'),['scripts/e2e/synthetic-journey.py'],{cwd:root,env:{...process.env,OTEL_EXPORTER_OTLP_ENDPOINT:'http://127.0.0.1:14318'},windowsHide:true,stdio:['ignore','inherit','inherit']});
 const timeout=setTimeout(()=>child.kill(),180000);
 let finished=false;async function done(code){if(finished)return;finished=true;clearTimeout(timeout);synthetic={success:Number(code===0),timestamp:Date.now()/1000};try{const r=JSON.parse(await readFile('artifacts/synthetic-journey.json','utf8'));synthetic.duration=r.seconds;}catch{}busy=false;setTimeout(journey,300000);}
 child.once('error',()=>done(1));child.once('exit',done);
}
function metrics(){let lines=[];const emit=(name,value,labels='')=>{if(Number.isFinite(value))lines.push(`${name}${labels} ${value}`);};
 for(const [service,v] of state){const label=`{service_name="${service}"}`;for(const [suffix,value] of Object.entries({success:v.success,duration_seconds:v.duration,last_run_timestamp_seconds:v.timestamp,attempts_total:v.total,successes_total:v.passed}))emit('poc_readiness_probe_'+suffix,value,label);}
 emit('poc_scheduled_journey_success',synthetic.success);emit('poc_scheduled_journey_last_run_timestamp_seconds',synthetic.timestamp);emit('poc_scheduled_journey_duration_seconds',synthetic.duration);
 emit('poc_host_memory_total_bytes',os.totalmem());emit('poc_host_memory_available_bytes',os.freemem());emit('poc_monitor_uptime_seconds',process.uptime());
 emit('poc_host_cpu_utilization_ratio',cpuRatio);
 for(const [key,value] of Object.entries(database))emit('poc_database_'+key,value);
 for(const [name,v] of rum){const label=`{metric="${name}"}`;emit('poc_browser_observation_sum',v.sum,label);emit('poc_browser_observation_count',v.count,label);emit('poc_browser_last_value',v.last,label);emit('poc_browser_last_observation_timestamp_seconds',v.timestamp,label);}
 return lines.join('\n')+'\n';
}
http.createServer((req,res)=>{if(req.url==='/rum'&&req.method==='POST'){ingestRum(req,res);return;}if(req.url!=='/metrics'&&req.url!=='/health'){res.writeHead(404);res.end();return;}res.setHeader('Content-Type','text/plain; version=0.0.4');res.end(req.url==='/metrics'?metrics():'UP');}).listen(9097,'127.0.0.1',()=>console.log('Native monitor: readiness every 10 seconds, fake-payment journey every 5 minutes'));
cycle();databaseProbe();if(process.env.POC_SYNTHETIC_ENABLED!=='false')setTimeout(journey,15000);
