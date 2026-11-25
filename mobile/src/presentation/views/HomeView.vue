<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  IonButton, IonButtons, IonCard, IonCardContent, IonCardHeader, IonCardSubtitle, IonCardTitle,
  IonContent, IonHeader, IonIcon, IonPage, IonSpinner, IonTitle, IonToolbar,
} from '@ionic/vue';
import {
  alertCircleOutline, checkmarkCircleOutline, closeCircleOutline, informationCircleOutline, refreshOutline,
} from 'ionicons/icons';
import type { ApiStatus } from '../../domain/model/ApiStatus';
import { useApplication } from '../context';
import { describeHealth, type Tone } from '../health-messages';

const app = useApplication();
const status = ref<ApiStatus | null>(null);
const loading = ref(false);

const message = computed(() => (status.value ? describeHealth(status.value) : null));
const icons: Record<Tone, string> = {
  success: checkmarkCircleOutline,
  warning: alertCircleOutline,
  danger: closeCircleOutline,
};

// Texto que leen los lectores de pantalla al cambiar el estado
const announcement = computed(() => {
  if (loading.value) return 'Comprobando la conexión con el servidor…';
  return message.value ? `${message.value.title}. ${message.value.detail}` : '';
});

async function refresh() {
  loading.value = true;
  try {
    status.value = await app.checkApiHealth();
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <IonPage>
    <IonHeader>
      <IonToolbar color="primary">
        <IonTitle>Gestor de Pedidos</IonTitle>
        <IonButtons slot="end">
          <IonButton router-link="/acerca" aria-label="Acerca de la aplicación">
            <IonIcon slot="icon-only" :icon="informationCircleOutline" aria-hidden="true" />
          </IonButton>
        </IonButtons>
      </IonToolbar>
    </IonHeader>
    <IonContent class="ion-padding">
      <h1 class="welcome">Bienvenido</h1>
      <p class="lead">Administra tus pedidos desde el móvil. Primero comprobamos la conexión con el servidor.</p>

      <IonCard>
        <IonCardHeader>
          <IonCardSubtitle class="subtitle">Estado del servidor</IonCardSubtitle>
          <IonCardTitle :class="['state', message ? `tone-${message.tone}` : '']">
            <template v-if="loading">
              <IonSpinner name="dots" aria-hidden="true" />
              <span class="state-text">Comprobando…</span>
            </template>
            <template v-else-if="message">
              <IonIcon :icon="icons[message.tone]" aria-hidden="true" />
              <span class="state-text">{{ message.title }}</span>
            </template>
            <span v-else class="state-text">Sin consultar</span>
          </IonCardTitle>
        </IonCardHeader>
        <IonCardContent>
          <template v-if="status && message && !loading">
            <p class="detail">{{ message.detail }}</p>
            <p class="meta">
              <template v-if="status.latencyMs !== null">Respuesta en {{ status.latencyMs }} ms · </template>
              Consultado a las {{ status.checkedAt.toLocaleTimeString('es-CO') }}
            </p>
          </template>
          <IonButton expand="block" :disabled="loading" @click="refresh">
            <IonIcon slot="start" :icon="refreshOutline" aria-hidden="true" />
            Volver a comprobar
          </IonButton>
        </IonCardContent>
      </IonCard>

      <!-- Región viva: anuncia el resultado sin mover el foco -->
      <p class="visually-hidden" role="status" aria-live="polite" aria-atomic="true">{{ announcement }}</p>
    </IonContent>
  </IonPage>
</template>

<style scoped>
.welcome { margin: 8px 0 4px; font-weight: 700; color: var(--app-text); }
.lead, .detail { color: var(--app-text); margin-top: 0; }
.subtitle, .meta { color: var(--app-text-muted); }
.meta { font-size: 0.875rem; }
.state { display: flex; align-items: center; gap: 8px; color: var(--app-text); }
.tone-success { color: var(--app-success-text); }
.tone-warning { color: var(--app-warning-text); }
.tone-danger { color: var(--app-danger-text); }
</style>
