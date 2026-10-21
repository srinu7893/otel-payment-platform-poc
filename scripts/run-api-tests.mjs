import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { execFileSync, spawnSync } from 'node:child_process';
import { payload, links, scrub, markdown, document, junit, uniqueExecutions } from './api-report.mjs';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');process.chdir(root);
const args=process.argv.slice(2);const suiteArg=args.find(a=>a.startsWith('--suite='))?.slice(8)||'all';
const definitions={live:['Live data','postman/Live-Data.postman_collection.json'],acceptance:['Isolated acceptance','postman/Payment-Platform.postman_collection.json','postman/Cloud.postman_environment.json'],rum:['Browser telemetry','postman/OTel-Browser.postman_collection.json']};
if(suiteArg!=='all' && !definitions[suiteArg])throw new Error('Use --suite=all, live, acceptance or rum');
const require=createRequire(import.meta.url);let newman;
try {newman=require(path.join(root,'artifacts/postman-tools/node_modules/newman'));}
catch {throw new Error('Install Newman first: npm install --prefix artifacts/postman-tools --no-save newman@6.2.3');}
const stamp=new Date().toISOString().replace(/[:.]/g,'-');const out=path.join(root,'artifacts/api-tests',stamp);fs.mkdirSync(out,{recursive:true});
const latestDirectory=path.join(root,'artifacts/api-tests/latest');fs.mkdirSync(latestDirectory,{recursive:true});
for(const key of Object.keys(definitions))fs.rmSync(path.join(latestDirectory,key+'.xml'),{force:true});
for(const name of ['api-logs.md','api-logs.json'])fs.rmSync(path.join(latestDirectory,name),{force:true});
const bodyDirectory=path.join(latestDirectory,'bodies');
if(fs.existsSync(bodyDirectory))for(const name of fs.readdirSync(bodyDirectory))if(/^(live|acceptance|rum)-\d+\.json$/.test(name))fs.rmSync(path.join(bodyDirectory,name));
const report={status:'RUNNING',startedAt:new Date().toISOString(),source:execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),workingTreeModified:Boolean(execFileSync('git',['status','--porcelain','--untracked-files=no'],{encoding:'utf8'}).trim()),suites:[]};
const read=file=>JSON.parse(fs.readFileSync(file,'utf8'));
const count=items=>items.reduce((n,item)=>n+(item.item?count(item.item):1),0);
function save(){
  fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(report,null,2));
  fs.writeFileSync(path.join(out,'api-tested.md'),markdown(report));
  fs.writeFileSync(path.join(out,'index.html'),document(report));
  for(const suite of report.suites)fs.writeFileSync(path.join(out,suite.key+'.xml'),junit(suite));
  const latest=path.join(root,'artifacts/api-tests/latest');fs.mkdirSync(latest,{recursive:true});
  for(const name of ['results.json','api-tested.md','index.html'])fs.copyFileSync(path.join(out,name),path.join(latest,name));
  for(const suite of report.suites){
    fs.copyFileSync(path.join(out,suite.key+'.xml'),path.join(latest,suite.key+'.xml'));
    for(const row of suite.requests)if(row.requestBody!==null){
      for(const base of [out,latest]){fs.mkdirSync(path.join(base,'bodies'),{recursive:true});fs.writeFileSync(path.join(base,row.bodyFile),JSON.stringify(row.requestBody,null,2));}
    }
  }
}
save();
for(const key of suiteArg==='all'?Object.keys(definitions):[suiteArg]){
  const [name,file,env]=definitions[key],collection=read(file);console.log(`RUN ${name} (${count(collection.item)} collection requests)`);
  const suite=await new Promise(resolve=>newman.run({collection,environment:env?read(env):undefined,insecure:false,timeoutRequest:180000,timeoutScript:240000,reporters:[]},(error,summary)=>{
    const executions=summary?.run?.executions||[];
    // Newman retains the same cursor object once for every pm.sendRequest;
    // deduplicate before counting collection requests and documenting responses.
    const main=uniqueExecutions(executions).filter(e=>e.assertions?.length);
    const failures=(summary?.run?.failures||[]).map(f=>({where:f.at,name:f.source?.name,message:scrub(f.error?.message||'Execution failure')}));
    if(error)failures.push({message:scrub(error.message)});
    const requests=main.map((e,index)=>{
      const header=k=>e.response?.headers?.get(k)||'';
      const raw=e.request?.body?.raw;const trace=header('X-Trace-Id');
      return {name:e.item.name,method:e.request.method,url:scrub(e.request.url.toString()),status:e.response?.code||0,durationMs:e.response?.responseTime||0,
        authenticated:Boolean(e.request.headers.get('Authorization')),
        requestHeaders:Object.fromEntries(['Content-Type','Origin','X-Correlation-Id','traceparent','Accept'].filter(k=>e.request.headers.get(k)).map(k=>[k,scrub(e.request.headers.get(k))])),
        bodyFile:`bodies/${key}-${index+1}.json`,
        requestBody:raw===undefined?null:payload(raw),responseBody:payload(e.response?.stream?.toString('utf8')||''),
        correlationId:header('X-Correlation-Id'),traceId:trace,links:links(trace),
        result:e.requestError || e.assertions.some(a=>a.error||a.skipped)?'FAIL':'PASS',
        assertions:e.assertions.map(a=>({name:scrub(a.assertion),passed:!a.error&&!a.skipped,...(a.error?{error:scrub(a.error.message)}:a.skipped?{error:'Assertion skipped; not proof of execution'}:{})}))};
    });
    const expectedRequests=count(collection.item);
    if(requests.length!==expectedRequests)failures.push({message:`Only ${requests.length}/${expectedRequests} requests produced assertion evidence; skipped requests are not passes.`});
    resolve({key,name,status:failures.length || requests.some(r=>r.result==='FAIL')?'FAIL':'PASS',expectedRequests,networkCalls:summary?.run?.stats?.requests?.total||0,assertions:summary?.run?.stats?.assertions?.total||0,requests,failures});
  }));
  report.suites.push(suite);save();console.log(`${suite.status}: ${name}, ${suite.requests.length} main requests, ${suite.assertions} assertions`);
}
report.status=report.suites.every(s=>s.status==='PASS')?'PASS':'FAIL';report.finishedAt=new Date().toISOString();save();
console.log(`${report.status}: ${path.relative(root,out)}\nOpen artifacts/api-tests/latest/index.html`);
if(args.includes('--open') && process.platform==='win32')spawnSync('cmd.exe',['/d','/c','start','',path.join(out,'index.html')],{stdio:'ignore'});
process.exitCode=report.status==='PASS'?0:1;
