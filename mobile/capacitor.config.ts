import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.neiderlizcano.gestorpedidos.ionic',
  appName: 'Gestor de Pedidos',
  webDir: 'dist',
  // En debug Capacitor escribe en logcat los argumentos y respuestas de cada plugin,
  // lo que incluiría la sesión con sus tokens. Se desactiva en todas las compilaciones.
  loggingBehavior: 'none',
  plugins: {
    CapacitorHttp: { enabled: true },
  },
};

export default config;
