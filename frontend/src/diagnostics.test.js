import test from 'node:test';
import assert from 'node:assert/strict';
import { recordDiagnostic, recentDiagnostics, cloudEvidenceLinks } from './diagnostics.js';
test('diagnostics retain bounded metadata without URLs, concrete IDs or query secrets', () => {
  for (let i=0; i<15; i++) recordDiagnostic('/v1/customers/private-customer?password=secret', 'GET',
    new Response('{}',{status:403,headers:{'X-Trace-Id':'1234567890abcdef1234567890abcdef','X-Correlation-Id':'safe-correlation'}}),'fallback');
  const rows=recentDiagnostics();
  assert.equal(rows.length,10);assert.equal(rows[0].operation,'/v1/customers/{id}');
  assert.equal(rows[0].traceId,'1234567890abcdef1234567890abcdef');
  assert.equal(JSON.stringify(rows).includes('secret'),false);
});
test('missing or invalid trace is never invented',()=>{
  recordDiagnostic('/v1/wallet/recipients/ACC1001','GET',new Response('{}',{headers:{'X-Trace-Id':'spoof'}}),'safe');
  assert.equal(recentDiagnostics()[0].traceId,'');
  assert.equal(recentDiagnostics()[0].operation,'/v1/wallet/recipients/{account}');
  assert.equal(cloudEvidenceLinks('project-c9bd3d0e-266f-47bf-852','spoof','safe').trace,'');
});
test('evidence links target the selected trace and safely encode the log query',()=>{
  const trace='1234567890abcdef1234567890abcdef';
  const links=cloudEvidenceLinks('project-c9bd3d0e-266f-47bf-852',trace,'safe');
  assert.equal(new URL(links.trace).searchParams.get('tid'),trace);
  assert.match(new URL(links.logs).searchParams.get('query'),new RegExp(trace));
  assert.deepEqual(cloudEvidenceLinks('invalid project',trace,'safe'),{});
});
