package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import android.content.Context;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;

/**
 * Almacenamiento de la sesión de Supabase para la WebView.
 * Cada valor se cifra con su propia clave del Android Keystore y se guarda en
 * noBackupFilesDir, que no se incluye en copias de seguridad ni migraciones.
 * Cualquier fallo se rechaza con el código STORAGE_ERROR. Una escritura solo se
 * confirma tras releerla; un borrado, cuando el valor ya no puede restaurarse.
 */
@CapacitorPlugin(name = "SecureSessionStorage")
public class SecureSessionStoragePlugin extends Plugin {

    private static final String TAG = "SecureSessionStorage";
    static final String KEY_ALIAS_PREFIX = "gestor_pedidos_session_v2_";
    static final String DIRECTORY = "secure_session";
    private static final int MAX_KEY_LENGTH = 256;

    private SecureFileStore store;

    // Ruta fuera de las copias de seguridad; expuesta para las pruebas
    public static File storageDirectory(Context context) {
        return new File(context.getNoBackupFilesDir(), DIRECTORY);
    }

    @Override
    public void load() {
        store = new SecureFileStore(storageDirectory(getContext()), new KeystoreKeyProvider(KEY_ALIAS_PREFIX));
    }

    @PluginMethod
    public void get(PluginCall call) {
        String key = validKey(call);
        if (key == null) return;
        try {
            SecureFileStore.ReadResult result = store.read(key);
            JSObject response = new JSObject();
            response.put("value", result.value == null ? JSObject.NULL : result.value);
            response.put("recovered", result.recovered);
            call.resolve(response);
        } catch (Exception e) {
            fail(call, "read", e, null);
        }
    }

    @PluginMethod
    public void set(PluginCall call) {
        String key = validKey(call);
        if (key == null) return;
        String value = call.getString("value");
        if (value == null) {
            call.reject("Falta el valor");
            return;
        }
        try {
            store.write(key, value);
            call.resolve();
        } catch (Exception e) {
            fail(call, "write", e, null);
        }
    }

    @PluginMethod
    public void remove(PluginCall call) {
        String key = validKey(call);
        if (key == null) return;
        try {
            // Resuelve solo si el valor ya no puede restaurarse; la limpieza física puede quedar pendiente
            SecureFileStore.DeleteResult result = store.delete(key);
            JSObject response = new JSObject();
            response.put("pendingCleanup", result.pendingCleanup);
            call.resolve(response);
        } catch (Exception e) {
            // Sin marca ni borrado confirmados: no se informa éxito
            fail(call, "remove", e, null);
        }
    }

    private static String validKey(PluginCall call) {
        String key = call.getString("key");
        if (key == null || key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
            call.reject("Clave no válida");
            return null;
        }
        return key;
    }

    // Solo se registra el tipo de error: nunca la clave lógica ni el valor
    private static void fail(PluginCall call, String operation, Exception e, JSObject data) {
        Log.e(TAG, "Fallo en " + operation + ": " + e.getClass().getSimpleName());
        call.reject("Error de almacenamiento seguro", "STORAGE_ERROR", data);
    }
}
