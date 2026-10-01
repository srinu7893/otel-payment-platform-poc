const AUTH_BASE = import.meta.env.VITE_AUTH_BASE_URL || 'http://localhost:8079';
const PAYMENT_BASE = import.meta.env.VITE_PAYMENT_BASE_URL || 'http://localhost:8080';

async function request(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    }
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(body || `HTTP ${response.status}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

export function login(username, password) {
  return request(`${AUTH_BASE}/api/v1/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });
}

export function listPayments(token) {
  return request(`${PAYMENT_BASE}/api/v1/payments?size=20`, {
    headers: { Authorization: `Bearer ${token}` }
  });
}

export function createPayment(token, payload) {
  return request(`${PAYMENT_BASE}/api/v1/payments`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify(payload)
  });
}
