import test from 'node:test';
import assert from 'node:assert/strict';
import {sanitize,payload,document,links,uniqueExecutions} from './api-report.mjs';
test('login helper executions do not inflate main request counts',()=>{
 const execution={cursor:{ref:'one'},assertions:[{assertion:'ok'}]};
 assert.equal(uniqueExecutions([execution,execution,{cursor:{ref:'two'},assertions:[]}]).length,2);
});
test('reports strip nested credentials, auth headers and bearer/JWT text',()=>{
 const result=sanitize({password:'TOPSECRET',nested:{accessToken:'TOKEN',Authorization:'Bearer secret'},error:'Bearer verysecret eyJhbGc.eyJzdWI.signature',items:[{refreshToken:'REFRESH'}]});
 const text=JSON.stringify(result);for(const secret of ['TOPSECRET','TOKEN','verysecret','eyJhbGc','REFRESH'])assert.ok(!text.includes(secret));
 assert.equal(result.password,'[REDACTED]');assert.equal(payload('{"password":"SECRET"}').password,'[REDACTED]');
 assert.ok(!JSON.stringify(sanitize({error:'Login failed with {"password":"QUOTED_SECRET"}'})).includes('QUOTED_SECRET'));
});
test('untrusted response HTML is escaped and invalid traces produce no links',()=>{
 assert.deepEqual(links('spoofed'),{});
 const report={status:'PASS',startedAt:'now',source:'test',suites:[{name:'x',status:'PASS',requests:[{name:'<script>bad()</script>',method:'GET',url:'https://example.com',status:200,durationMs:1,requestBody:null,responseBody:'<img src=x onerror=bad()>',assertions:[],links:{}}],expectedRequests:1,networkCalls:1,assertions:0,failures:[]}]};
 const rendered=document(report);assert.ok(!rendered.includes('<script>bad()'));assert.ok(!rendered.includes('<img src=x'));assert.ok(rendered.includes('&lt;img'));
});
