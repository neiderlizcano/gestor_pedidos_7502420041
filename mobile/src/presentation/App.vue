<script setup lang="ts">
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { IonApp, IonRouterOutlet, useBackButton, useIonRouter } from '@ionic/vue';

const ionRouter = useIonRouter();

// Prioridad -1: el router atiende primero; sin historial la app pasa a segundo plano como en Android
useBackButton(-1, () => {
  if (Capacitor.isNativePlatform() && !ionRouter.canGoBack()) void App.minimizeApp();
});
</script>

<template>
  <IonApp>
    <IonRouterOutlet />
  </IonApp>
</template>
