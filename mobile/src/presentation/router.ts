import { createRouter, createWebHistory } from '@ionic/vue-router';

const HomeView = () => import('./views/HomeView.vue');
const AboutView = () => import('./views/AboutView.vue');

export function createAppRouter() {
  return createRouter({
    history: createWebHistory(),
    routes: [
      { path: '/', redirect: '/inicio' },
      { path: '/inicio', component: HomeView },
      { path: '/acerca', component: AboutView },
      { path: '/:pathMatch(.*)*', redirect: '/inicio' },
    ],
  });
}
