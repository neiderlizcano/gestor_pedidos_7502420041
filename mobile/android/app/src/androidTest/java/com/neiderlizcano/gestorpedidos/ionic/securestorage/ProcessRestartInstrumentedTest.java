package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Comprueba la marca de invalidación a través de un reinicio real del proceso.
 * Cada fase se lanza con una invocación distinta de `am instrument`, que crea un proceso nuevo:
 *   -e restartPhase prepare  → guarda una sesión y la invalida con clave y archivo imposibles de borrar
 *   -e restartPhase verify   → en otro proceso, comprueba que no se restaura y que se recupera
 * Sin el argumento, ambas fases se omiten (por ejemplo, en connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4.class)
public class ProcessRestartInstrumentedTest {

    private static final String PREFIX = "gestor_pedidos_restart_test_";
    private static final String KEY = "gestor-pedidos-auth:reinicio";
    private static final String VALUE = "{\"refresh_token\":\"refresh-de-prueba\"}";

    private static String phase() {
        Bundle args = InstrumentationRegistry.getArguments();
        return args.getString("restartPhase");
    }

    private static File directory() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new File(context.getNoBackupFilesDir(), "secure_session_restart_test");
    }

    @Test
    public void prepare() throws Exception {
        assumeTrue("prepare".equals(phase()));
        SecureFileStore store = new SecureFileStore(directory(), new KeystoreKeyProvider(PREFIX));
        store.clear();
        store.write(KEY, VALUE);

        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        SecureFileStore failing = new SecureFileStore(directory(), new FaultyStorage.StickyKeys(PREFIX), files);
        assertTrue(failing.delete(KEY).pendingCleanup);
        assertTrue(store.fileFor(KEY).exists());
        assertTrue(store.markerFor(KEY).exists());
    }

    @Test
    public void verify() throws Exception {
        assumeTrue("verify".equals(phase()));
        KeystoreKeyProvider keys = new KeystoreKeyProvider(PREFIX);
        SecureFileStore store = new SecureFileStore(directory(), keys);
        // Tras el reinicio, clave y archivo antiguos siguen ahí hasta esta lectura
        assertTrue(keys.hasKey(SecureFileStore.itemId(KEY)));

        assertNull(store.read(KEY).value);
        assertFalse(store.fileFor(KEY).exists());
        assertFalse(store.markerFor(KEY).exists());

        // Recuperación: una sesión nueva se guarda y se lee con normalidad
        store.write(KEY, "sesión nueva");
        assertEquals("sesión nueva", store.read(KEY).value);
        store.clear();
    }
}
