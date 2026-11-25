/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const target = env.API_PROXY_TARGET || 'https://gestor-pedidos-api.onrender.com';
  // El proxy evita CORS en el navegador; Android usa VITE_NATIVE_API_BASE_URL
  const proxy = {
    '/api': { target, changeOrigin: true },
    '/actuator': { target, changeOrigin: true },
  };
  return {
    plugins: [vue()],
    server: { port: 5174, strictPort: true, proxy },
    preview: { port: 4174, strictPort: true, proxy },
    test: { environment: 'node', include: ['tests/**/*.spec.ts'] },
  };
});
