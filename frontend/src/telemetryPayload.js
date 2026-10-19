export const LIMITS = Object.freeze({ CLS: 5, FCP: 120000, INP: 120000, LCP: 120000, TTFB: 120000, PAGE_VIEW: 1, JS_ERROR: 1 });
export function createReporter(deliver) {
  let remaining = 10;
  const sent = new Map();
  return (name, value) => {
    if (!Object.hasOwn(LIMITS, name) || !Number.isFinite(value) || value < 0 || value > LIMITS[name]) return false;
    if ((name === 'PAGE_VIEW' || name === 'JS_ERROR') && value !== 1) return false;
    if (!remaining || (sent.get(name) || 0) >= (name === 'JS_ERROR' ? 3 : 1)) return false;
    remaining--; sent.set(name, (sent.get(name) || 0) + 1);
    try { Promise.resolve(deliver({ name, value })).catch(() => {}); } catch { /* telemetry cannot break the UI */ }
    return true;
  };
}
