import test from 'node:test';
import assert from 'node:assert/strict';
import { getWallet, depositMoney } from './api.js';
const response = (status, data) => new Response(JSON.stringify(data), {status, headers: {'Content-Type':'application/json'}});
test('wallet GET retries transient startup responses with the same correlation ID', async t => {
  const calls=[];
  t.mock.method(globalThis,'fetch',async (url,options)=>{calls.push(options);return calls.length<3 ? response(503,{code:'BACKEND_UNAVAILABLE',message:'Starting'}) : response(200,{balance:10});});
  assert.equal((await getWallet('test-customer-token')).balance,10);
  assert.equal(calls.length,3);
  assert.equal(new Set(calls.map(c=>c.headers['X-Correlation-Id'])).size,1);
});
test('money-changing POST is never automatically retried', async t => {
  let calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;return response(503,{code:'BACKEND_UNAVAILABLE',message:'Starting'});});
  await assert.rejects(depositMoney('token',{idempotencyKey:'key',amount:1}),e=>e.status===503);
  assert.equal(calls,1);
});
test('GET retries are bounded and do not retry genuine application errors', async t => {
  let calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;return response(503,{code:'BACKEND_UNAVAILABLE',message:'Starting'});});
  await assert.rejects(getWallet('token'),e=>e.status===503);assert.equal(calls,3);
  calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;return response(500,{code:'INTERNAL_ERROR',message:'Error'});});
  await assert.rejects(getWallet('token'),e=>e.status===500);assert.equal(calls,1);
});
