import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import AppErrorBoundary from './AppErrorBoundary';
import './styles.css';
import { startTelemetry } from './telemetry';

try { startTelemetry(); } catch { /* telemetry setup must never prevent the app from rendering */ }

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AppErrorBoundary><App /></AppErrorBoundary>
  </React.StrictMode>
);
