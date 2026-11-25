import { createApp } from 'vue';
import { IonicVue } from '@ionic/vue';
import '@ionic/vue/css/core.css';
import '@ionic/vue/css/normalize.css';
import '@ionic/vue/css/structure.css';
import '@ionic/vue/css/typography.css';
import '@ionic/vue/css/padding.css';
import App from './presentation/App.vue';
import { createAppRouter } from './presentation/router';
import { USE_CASES } from './presentation/context';
import { useCases } from './bootstrap/container';
import './presentation/theme/variables.css';

const router = createAppRouter();
const app = createApp(App).provide(USE_CASES, useCases).use(IonicVue, { mode: 'md' }).use(router);

void router.isReady().then(() => app.mount('#app'));
