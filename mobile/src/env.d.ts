/// <reference types="vite/client" />

// import.meta.env no expone variables VITE_*: la configuración pública llega solo por aquí
declare const __GESTOR_PUBLIC_ENV__: Partial<
  Record<'VITE_API_BASE_URL' | 'VITE_NATIVE_API_BASE_URL' | 'VITE_SUPABASE_URL' | 'VITE_SUPABASE_PUBLISHABLE_KEY', string>
>;

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}
