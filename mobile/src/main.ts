import { createApp } from 'vue';
import { IonicVue } from '@ionic/vue';
import '@ionic/vue/css/core.css';
import '@ionic/vue/css/normalize.css';
import '@ionic/vue/css/structure.css';
import '@ionic/vue/css/typography.css';
import '@ionic/vue/css/padding.css';
import App from './presentation/App.vue';
import { createAppRouter } from './presentation/router';
import { AUTH_USE_CASES, USE_CASES } from './presentation/context';
import { useCases } from './bootstrap/container';
import { authUseCases } from './bootstrap/auth-container';
import { isAuthError } from './domain/exceptions/AuthError';
import './presentation/theme/variables.css';

const router = createAppRouter();
const app = createApp(App)
  .provide(USE_CASES, useCases)
  .provide(AUTH_USE_CASES, authUseCases)
  .use(IonicVue, { mode: 'md' })
  .use(router);

// Recupera la sesión guardada sin bloquear la primera pantalla; solo se registra el código
void authUseCases.initialize().catch((error: unknown) => {
  console.warn('Sesión no recuperada:', isAuthError(error) ? error.code : 'UNKNOWN');
});

void router.isReady().then(() => app.mount('#app'));
