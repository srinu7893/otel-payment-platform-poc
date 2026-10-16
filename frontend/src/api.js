const API_BASE = import.meta.env?.VITE_API_BASE_URL || '/api';
import { normalizeApiError } from './errors.js';
import { recordDiagnostic } from './diagnostics.js';

async function request(path, options = {}) {
  const correlationId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`;
  const response = await fetchWithReadRetry(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
      ...(options.headers || {})
    }
  });

  recordDiagnostic(path, options.method || 'GET', response, correlationId);
  const contentType = response.headers.get('content-type') || '';
  const body = response.status === 204
    ? null
    : contentType.includes('application/json')
      ? await response.json()
      : await response.text();

  if (!response.ok) {
    const normalized = normalizeApiError({ response: { data: body, headers: response.headers } });
    const error = new Error(normalized.message);
    error.status = response.status;
    error.code = normalized.code;
    error.correlationId = normalized.correlationId || correlationId;
    throw error;
  }
  return body;
}

async function fetchWithReadRetry(url, options) {
  const read = (options.method || 'GET').toUpperCase() === 'GET';
  for (let attempt = 0; ; attempt++) {
    try {
      const response = await fetch(url, options);
      if (!read || attempt >= 2 || ![429, 502, 503, 504].includes(response.status)) return response;
      await response.body?.cancel();
    } catch (error) {
      if (!read || attempt >= 2) throw error;
    }
    await new Promise(resolve => setTimeout(resolve, 250 * (attempt + 1)));
  }
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function login(username, password) {
  return request('/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });
}

export function register(payload) {
  return request('/v1/auth/register', { method: 'POST', body: JSON.stringify(payload) });
}

export function getCustomer(token, customerId) {
  return request(`/v1/customers/${customerId}`, { headers: authHeaders(token) });
}

export function listCustomers(token) {
  return request('/v1/customers', { headers: authHeaders(token) });
}

export function listPayments(token, { page = 0, size = 20, status } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return request(`/v1/payments?${params.toString()}`, { headers: authHeaders(token) });
}

export function getPayment(token, paymentId) {
  return request(`/v1/payments/${paymentId}`, { headers: authHeaders(token) });
}

export function createPayment(token, payload) {
  return request('/v1/payments', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
}

export function cancelPayment(token, paymentId) {
  return request(`/v1/payments/${paymentId}/cancel`, {
    method: 'POST',
    headers: authHeaders(token)
  });
}

export function createRefund(token, paymentId, idempotencyKey = crypto.randomUUID()) {
  return request(`/v1/payments/${paymentId}/refunds`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ idempotencyKey })
  });
}

export function getRefund(token, paymentId) {
  return request(`/v1/payments/${paymentId}/refund`, { headers: authHeaders(token) });
}

export function listTransfers(token, { page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return request(`/v1/transfers?${params.toString()}`, { headers: authHeaders(token) });
}

export function getTransfer(token, transferId) {
  return request(`/v1/transfers/${transferId}`, { headers: authHeaders(token) });
}

export function createTransfer(token, payload) {
  return request('/v1/transfers', {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload)
  });
}

export function listNotifications(token, { paymentId, transferId, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (paymentId) params.set('paymentId', paymentId);
  if (transferId) params.set('transferId', transferId);
  return request(`/v1/notifications?${params.toString()}`, { headers: authHeaders(token) });
}

export function getOperationsHealth(token) {
  return request('/v1/ops/health', { headers: authHeaders(token) });
}

export const getWallet = token => request('/v1/wallet', { headers: authHeaders(token) });
export const lookupRecipient = (token, account) => request(`/v1/wallet/recipients/${encodeURIComponent(account)}`, { headers: authHeaders(token) });
export const getActivity = (token, page = 0) => request(`/v1/wallet/activity?page=${page}&size=20`, { headers: authHeaders(token) });
export const depositMoney = (token, payload) => request('/v1/wallet/deposits', { method: 'POST', headers: authHeaders(token), body: JSON.stringify(payload) });
export const listMoneyRequests = (token, page = 0) => request(`/v1/money-requests?page=${page}&size=20`, { headers: authHeaders(token) });
export const requestMoney = (token, payload) => request('/v1/money-requests', { method: 'POST', headers: authHeaders(token), body: JSON.stringify(payload) });
export const respondToRequest = (token, id, action) => request(`/v1/money-requests/${encodeURIComponent(id)}/${action}`, { method: 'POST', headers: authHeaders(token) });
