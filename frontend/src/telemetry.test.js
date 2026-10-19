import test from 'node:test';
import assert from 'node:assert/strict';
import { createReporter } from './telemetryPayload.js';
test('RUM strips extra fields and rejects unknown names and unbounded values', () => {
  const rows = []; const send = createReporter(row => rows.push(row));
  assert.equal(send('PAGE_VIEW', 1, { account: 'SECRET' }), true);
  for (const [name, value] of [['SECRET',1],['LCP',Infinity],['LCP',-1],['CLS',6],['JS_ERROR',0]]) assert.equal(send(name,value),false);
  assert.deepEqual(rows,[{ name:'PAGE_VIEW', value:1 }]);
});
test('RUM bounds events and delivery failures never escape', () => {
  const send = createReporter(() => { throw Error('network'); });
  assert.equal(send('PAGE_VIEW',1),true); assert.equal(send('PAGE_VIEW',1),false);
  for(let i=0;i<3;i++)assert.equal(send('JS_ERROR',1),true);
  assert.equal(send('JS_ERROR',1),false);
});
