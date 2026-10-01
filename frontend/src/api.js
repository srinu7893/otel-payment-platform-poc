const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

async function request(path, options = {}) {
  const correlationId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random()}`;
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-Id': correlationId,
      ...(options.headers || {})
    }
  });

  const contentType = response.headers.get('content-type') || '';
  const body = response.status === 204
    ? null
    : contentType.includes('application/json')
      ? await response.json()
      : await response.text();

  if (!response.ok) {
    const message = typeof body === 'object' && body?.message
      ? body.message
      : body || `HTTP ${response.status}`;
    const error = new Error(message);
    error.status = response.status;
    error.correlationId = response.headers.get('X-Correlation-Id') || correlationId;
    throw error;
  }
  return body;
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

export function listNotifications(token) {
  return request('/v1/notifications', { headers: authHeaders(token) });
}

export function listCustomers(token) {
  return request('/v1/customers', { headers: authHeaders(token) });
}
