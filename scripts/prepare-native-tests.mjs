import {spawnSync} from 'node:child_process';
const args=['-h','127.0.0.1','-p','55432','-U','payments'];
const env={...process.env,PGPASSWORD:'payments'};
const pg=process.env.PG_HOME||'C:/Program Files/PostgreSQL/17';
let r=spawnSync(pg+'/bin/psql.exe',[...args,'-d','postgres','-tAc',"select 1 from pg_database where datname='poc_integration'"],{env,windowsHide:true,encoding:'utf8'});
if(r.status!==0)throw Error(r.stderr);
if(r.stdout.trim()!=='1'){r=spawnSync(pg+'/bin/createdb.exe',[...args,'poc_integration'],{env,windowsHide:true,stdio:'inherit'});if(r.status!==0)throw Error('create test DB failed');}
for(const [url,body] of [['vhosts/poc-integration',{}],['permissions/poc-integration/payments',{configure:'.*',write:'.*',read:'.*'}]]){
 const response=await fetch('http://127.0.0.1:15672/api/'+url,{method:'PUT',headers:{Authorization:'Basic '+Buffer.from('payments:payments').toString('base64'),'Content-Type':'application/json'},body:JSON.stringify(body)});if(!response.ok)throw Error(`RabbitMQ setup: ${response.status}`);
}
console.log('Isolated integration database and RabbitMQ virtual host ready');
