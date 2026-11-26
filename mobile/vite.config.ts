/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import { buildPublicEnv } from './src/bootstrap/public-env';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  // Valida y filtra antes de servir o compilar; un valor inválido detiene el proceso
  const { publicEnv, ignoredNames } = buildPublicEnv(env);
  if (ignoredNames.length > 0) {
    console.warn(`Variables VITE_* no autorizadas, no se incluyen en la app: ${ignoredNames.join(', ')}`);
  }

  const target = env.API_PROXY_TARGET || 'https://gestor-pedidos-api.onrender.com';
  // El proxy evita CORS en el navegador; Android usa VITE_NATIVE_API_BASE_URL
  const proxy = {
    '/api': { target, changeOrigin: true },
    '/actuator': { target, changeOrigin: true },
  };
  return {
    plugins: [vue()],
    // Sin prefijos no hay exposición automática en import.meta.env (Vite 7 lo admite);
    // solo se publica la lista cerrada y validada de public-env.ts
    envPrefix: [],
    define: { __GESTOR_PUBLIC_ENV__: JSON.stringify(publicEnv) },
    server: { port: 5174, strictPort: true, proxy },
    preview: { port: 4174, strictPort: true, proxy },
    test: { environment: 'node', include: ['tests/**/*.spec.ts'] },
  };
});
