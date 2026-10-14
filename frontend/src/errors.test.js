import test from 'node:test';
import assert from 'node:assert/strict';
import { formatApiError, normalizeApiError } from './errors.js';

test('structured message, code and correlation use documented precedence', () => {
  const error = { response: { data: { message: 'Unexpected gateway processing error', code: 'GATEWAY_ERROR', correlationId: 'corr-1', correlation_id: 'corr-2' }, headers: { 'x-correlation-id': 'corr-3' } } };
  assert.equal(formatApiError(error), 'GATEWAY_ERROR: Unexpected gateway processing error\nCorrelation: corr-1');
});
test('nested messages, snake case and response headers are supported', () => {
  assert.equal(normalizeApiError({ response: { data: { error: { message: 'Retry later' }, correlation_id: 'corr-2' } } }).message, 'Retry later');
  assert.equal(normalizeApiError({ response: { data: 'Retry later', headers: new Headers({ 'x-correlation-id': 'corr-3' }) } }).correlationId, 'corr-3');
});
test('string errors and Error.message render safely', () => {
  assert.equal(formatApiError('Please retry'), 'Please retry');
  assert.equal(formatApiError(new Error('Network unavailable')), 'Network unavailable');
});
test('objects cannot become object Object or expose nested exception details', () => {
  for (const error of [{}, { response: { data: { message: { detail: 'secret' }, stack: 'private stack' } } }]) {
    assert.equal(formatApiError(error), 'Request failed');
  }
});
test('tokens, internal URLs, stack traces and credential assignments are never rendered', () => {
  for (const message of ['Bearer private-token', 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.signature', 'failure at https://auth.run.app', 'Error\n    at InternalClass.java:42', 'password=demo123', 'secret=jwt-secret', 'JWT_SECRET', 'neon-password', '[object Object]']) {
    assert.equal(formatApiError(new Error(message)), 'Request failed');
  }
});
