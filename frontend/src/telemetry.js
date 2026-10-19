import { onCLS, onFCP, onINP, onLCP, onTTFB } from 'web-vitals';
import { createReporter } from './telemetryPayload.js';

// Bounded same-origin measurements in production and development.
export function startTelemetry() {
  if (window.__pocTelemetryStarted) return;
  window.__pocTelemetryStarted = true;
  const send = createReporter(payload => fetch('/api/v1/telemetry/rum', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload), credentials: 'omit', keepalive: true,
  }).catch(() => {}));
  for (const observe of [onCLS, onFCP, onINP, onLCP, onTTFB]) {
    observe(({ name, value }) => send(name, value));
  }
  send('PAGE_VIEW', 1);
  window.addEventListener('error', () => send('JS_ERROR', 1));
  window.addEventListener('unhandledrejection', () => send('JS_ERROR', 1));
}
