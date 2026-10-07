import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/__poc/rum': {
        target: 'http://127.0.0.1:9097',
        rewrite: () => '/rum'
      },
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api/, '/api')
      }
    }
  }
});
