// Almacén clave-valor asíncrono para los datos de sesión del proveedor.
// Coincide con la interfaz de almacenamiento que acepta Supabase Auth.
export interface SessionStore {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}
