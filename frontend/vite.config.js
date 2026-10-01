import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/auth-api': {
        target: 'http://localhost:8079',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/auth-api/, '')
      },
      '/payment-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/payment-api/, '')
      }
    }
  }
});
