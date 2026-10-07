import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
await mkdir('artifacts/native',{recursive:true});
async function json(url){const r=await fetch(url);if(!r.ok)throw Error(`HTTP ${r.status}`);return r.json();}
const manifest=await json('https://www.python.org/ftp/python/3.13.7/windows-3.13.7.json');
await writeFile('artifacts/native/python-manifest.json',JSON.stringify(manifest,null,2));
const python=manifest.versions.find(v=>v.id==='pythoncore-3.13-64');
const assets=[['python',python.url,python.hash.sha256],['alertmanager','https://github.com/prometheus/alertmanager/releases/download/v0.28.1/alertmanager-0.28.1.windows-amd64.zip','521de9569ab0570845c38889cceb26790696ab04b1cba0b0086994b785013ca8']];
for(const [name,url,sha] of assets){
 const archive='artifacts/native/'+url.split('/').pop();let bytes;
 try{bytes=await readFile(archive);}catch{}
 const hash=b=>createHash('sha256').update(b).digest('hex');
 if(!bytes||hash(bytes)!==sha){const r=await fetch(url);if(!r.ok)throw Error(`${name}: HTTP ${r.status}`);bytes=Buffer.from(await r.arrayBuffer());if(hash(bytes)!==sha)throw Error(`${name}: checksum mismatch`);await writeFile(archive,bytes);}
 const target=`artifacts/native/${name}`;await mkdir(target,{recursive:true});
 const r=spawnSync('C:/Windows/System32/tar.exe',['-xf',archive,'-C',target],{windowsHide:true,stdio:'inherit'});if(r.status!==0)throw Error(`${name}: extract failed`);
 console.log(`Ready: ${name}; official SHA256 verified`);
}
