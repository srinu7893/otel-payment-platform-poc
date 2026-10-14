const FALLBACK = 'Request failed';

function safeText(value) {
  if (typeof value !== 'string' || !value.trim()) return undefined;
  if (/\[object Object\]|https?:\/\/|\bBearer\s|\b[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b|\n\s*at\s|(?:password|secret|token|credential)\s*[=:]|\b(?:jwt-secret|neon-(?:jdbc-url|username|password)|rabbit-(?:username|password)|JWT_SECRET)\b/i.test(value)) return undefined;
  return value.trim().slice(0, 500);
}

export function normalizeApiError(error) {
  const data = error?.response?.data;
  const headers = error?.response?.headers;
  const headerId = headers?.get?.('x-correlation-id') || headers?.['x-correlation-id'];
  const message = safeText(data?.message) || safeText(data?.error?.message)
    || safeText(data) || safeText(error instanceof Error ? error.message : undefined)
    || safeText(typeof error === 'string' ? error : undefined) || FALLBACK;
  const correlationId = [data?.correlationId, data?.correlation_id, headerId, error?.correlationId]
    .find(value => typeof value === 'string' && /^[A-Za-z0-9_:.-]{1,128}$/.test(value) && safeText(value));
  const rawCode = data?.code || error?.code;
  const code = typeof rawCode === 'string' && /^[A-Z][A-Z0-9_]{1,79}$/.test(rawCode) && safeText(rawCode) ? rawCode : undefined;
  return { message, correlationId, code };
}

export function formatApiError(error) {
  const { message, correlationId, code } = normalizeApiError(error);
  return `${code ? `${code}: ` : ''}${message}${correlationId ? `\nCorrelation: ${correlationId}` : ''}`;
}
