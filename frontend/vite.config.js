import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [react()],
  build: { rollupOptions: { input: {
    app: fileURLToPath(new URL('./index.html', import.meta.url)),
    apiDocs: fileURLToPath(new URL('./api-docs/index.html', import.meta.url))
  } } },
  server: {
    port: 3000,
    proxy: {
      '/__poc/rum': {
        target: 'http://127.0.0.1:9097',
        rewrite: () => '/rum'
      },
      '/api/': {
        target: process.env.API_GATEWAY_URL || 'http://localhost:8088',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, '/api')
      }
    }
  }
});
