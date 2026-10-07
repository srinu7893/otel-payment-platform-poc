import { onCLS, onFCP, onINP, onLCP, onTTFB } from 'web-vitals';

// Native development only. No URLs, error messages, DOM, credentials or user IDs.
export function startTelemetry() {
  if (!import.meta.env.DEV || window.__pocTelemetryStarted) return;
  window.__pocTelemetryStarted = true;
  const send = (name, value) => {
    if (!Number.isFinite(value)) return;
    fetch('/__poc/rum', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, value }), keepalive: true }).catch(() => {});
  };
  for (const observe of [onCLS, onFCP, onINP, onLCP, onTTFB]) {
    observe(({ name, value }) => send(name, value));
  }
  send('PAGE_VIEW', 1);
  window.addEventListener('error', () => send('JS_ERROR', 1));
  window.addEventListener('unhandledrejection', () => send('JS_ERROR', 1));
}
