<script setup lang="ts">
import {
  IonBackButton, IonButtons, IonContent, IonHeader, IonItem, IonLabel, IonList, IonPage, IonTitle, IonToolbar,
} from '@ionic/vue';
</script>

<template>
  <IonPage>
    <IonHeader>
      <IonToolbar color="primary">
        <IonButtons slot="start">
          <IonBackButton default-href="/inicio" text="Volver" aria-label="Volver" />
        </IonButtons>
        <IonTitle>Acerca de</IonTitle>
      </IonToolbar>
    </IonHeader>
    <IonContent>
      <h1 class="visually-hidden">Acerca de Gestor de Pedidos</h1>
      <IonList>
        <IonItem>
          <IonLabel>
            <h2>Gestor de Pedidos</h2>
            <p>Cliente Ionic Vue · Unidad 4</p>
          </IonLabel>
        </IonItem>
        <IonItem>
          <IonLabel>
            <h2>Arquitectura</h2>
            <p>Dominio, aplicación, adaptadores y presentación</p>
          </IonLabel>
        </IonItem>
      </IonList>
    </IonContent>
  </IonPage>
</template>

<style scoped>
/* El gris por defecto de Ionic queda justo en contraste; se oscurece */
ion-label p { color: var(--app-text-muted) !important; }
</style>
