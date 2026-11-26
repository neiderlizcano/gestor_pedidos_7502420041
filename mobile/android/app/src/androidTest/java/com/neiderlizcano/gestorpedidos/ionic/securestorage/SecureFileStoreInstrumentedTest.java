package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// Usa el Keystore real del dispositivo con un prefijo y un directorio propios de la prueba
@RunWith(AndroidJUnit4.class)
public class SecureFileStoreInstrumentedTest {

    private static final String TEST_PREFIX = "gestor_pedidos_session_test_";
    private static final String KEY = "gestor-pedidos-auth:proyecto";
    private static final String VALUE = "{\"access_token\":\"token-de-prueba\",\"refresh_token\":\"refresh-de-prueba\"}";

    private Context context;
    private File directory;
    private KeystoreKeyProvider keys;
    private SecureFileStore store;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        directory = new File(context.getNoBackupFilesDir(), "secure_session_test");
        keys = new KeystoreKeyProvider(TEST_PREFIX);
        store = new SecureFileStore(directory, keys);
        store.clear();
    }

    @After
    public void tearDown() throws Exception {
        new SecureFileStore(directory, keys).clear();
        directory.delete();
    }

    private SecureFileStore reopened() {
        // Objetos nuevos sobre los mismos archivos y claves (no reinicia el proceso)
        return new SecureFileStore(directory, new KeystoreKeyProvider(TEST_PREFIX));
    }

    // --- Operaciones básicas

    @Test
    public void writesAndReadsValue() throws Exception {
        store.write(KEY, VALUE);
        SecureFileStore.ReadResult result = store.read(KEY);
        assertEquals(VALUE, result.value);
        assertFalse(result.recovered);
    }

    @Test
    public void missingValueIsNullWithoutRecovery() throws Exception {
        SecureFileStore.ReadResult result = store.read(KEY);
        assertNull(result.value);
        assertFalse(result.recovered);
    }

    @Test
    public void overwritesValue() throws Exception {
        store.write(KEY, VALUE);
        store.write(KEY, "nuevo");
        assertEquals("nuevo", store.read(KEY).value);
    }

    @Test
    public void deleteRemovesFileKeyAndMarker() throws Exception {
        store.write(KEY, VALUE);
        assertFalse(store.delete(KEY).pendingCleanup);
        assertNull(store.read(KEY).value);
        assertFalse(store.fileFor(KEY).exists());
        assertFalse(store.markerFor(KEY).exists());
        assertFalse(keys.hasKey(SecureFileStore.itemId(KEY)));
    }

    @Test
    public void itemsHaveIndependentKeys() throws Exception {
        store.write(KEY, VALUE);
        store.write(KEY + "-code-verifier", "verificador");
        store.delete(KEY + "-code-verifier");
        assertEquals(VALUE, store.read(KEY).value);
    }

    @Test
    public void readsValueFromRecreatedInstances() throws Exception {
        store.write(KEY, VALUE);
        assertEquals(VALUE, reopened().read(KEY).value);
    }

    @Test
    public void storesCiphertextOnly() throws Exception {
        store.write(KEY, VALUE);
        String raw = new String(new StorageFiles().read(store.fileFor(KEY)), StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("token-de-prueba"));
        assertFalse(raw.contains("refresh_token"));
    }

    // --- Fallos de escritura

    @Test
    public void writeFailureKeepsPreviousValue() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failWrite = true;
        assertThrows(IOException.class, () -> new SecureFileStore(directory, keys, files).write(KEY, "nuevo"));
        assertEquals(VALUE, store.read(KEY).value);
        assertNoTempFiles();
    }

    @Test
    public void renameFailureIsReportedAndKeepsPreviousValue() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failRename = true;
        assertThrows(IOException.class, () -> new SecureFileStore(directory, keys, files).write(KEY, "nuevo"));
        assertEquals(VALUE, store.read(KEY).value);
        assertNoTempFiles();
    }

    @Test
    public void unverifiedWriteIsReported() throws Exception {
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.fakeRename = true;
        assertThrows(IOException.class, () -> new SecureFileStore(directory, keys, files).write(KEY, VALUE));
        assertNull(store.read(KEY).value);
    }

    // --- Invalidación persistente

    @Test
    public void doubleDeleteFailureIsBlockedByMarker() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        SecureFileStore failing = new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files);

        // Clave y archivo siguen ahí, pero la marca quedó confirmada
        assertTrue(failing.delete(KEY).pendingCleanup);
        assertTrue(store.fileFor(KEY).exists());
        assertTrue(keys.hasKey(SecureFileStore.itemId(KEY)));
        assertTrue(store.markerFor(KEY).exists());

        // Lectura posterior con el mismo almacén que falla: no devuelve la sesión
        assertNull(failing.read(KEY).value);
        // Reapertura con almacén sano: tampoco, y completa la limpieza
        assertNull(reopened().read(KEY).value);
        assertFalse(store.fileFor(KEY).exists());
        assertFalse(keys.hasKey(SecureFileStore.itemId(KEY)));
        assertFalse(store.markerFor(KEY).exists());
    }

    @Test
    public void markerSurvivesWhileCleanupKeepsFailing() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        files.failMarkerDelete = true;
        SecureFileStore failing = new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files);
        failing.delete(KEY);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertNull(new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files).read(KEY).value);
        }
        // La marca no se retira mientras clave o archivo sigan existiendo
        assertTrue(store.markerFor(KEY).exists());
    }

    @Test
    public void newSessionReplacesRevokedOneInControlledWay() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        files.failMarkerDelete = true;
        new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files).delete(KEY);

        // Aunque la marca no se pueda retirar, el valor nuevo tiene otro resumen y se lee
        SecureFileStore stillFailing = new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files);
        stillFailing.write(KEY, "otra sesión");
        assertEquals("otra sesión", stillFailing.read(KEY).value);
        assertEquals("otra sesión", reopened().read(KEY).value);
    }

    // --- Formato estricto de la marca

    private static final String MARKER_HEADER = "revoked-v1:";

    // Material revocado que no se puede borrar: clave, archivo y marca persisten
    private SecureFileStore stuckStore() {
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        files.failMarkerDelete = true;
        return new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files);
    }

    private void assertMarkerFailsClosed(String markerContent) throws Exception {
        store.write(KEY, VALUE);
        writeRaw(store.markerFor(KEY), markerContent.getBytes(StandardCharsets.UTF_8));

        assertNull(stuckStore().read(KEY).value);
        // Reapertura: sigue sin devolver las credenciales anteriores
        assertNull(stuckStore().read(KEY).value);
        // La marca no se retira mientras el material revocado siga existiendo
        assertTrue(store.markerFor(KEY).exists());
        assertTrue(store.fileFor(KEY).exists());
    }

    private String currentDigest() throws Exception {
        return SecureFileStore.digest(new StorageFiles().read(store.fileFor(KEY)));
    }

    @Test
    public void validMarkerForCurrentValueBlocksIt() throws Exception {
        store.write(KEY, VALUE);
        assertMarkerFailsClosedFor(MARKER_HEADER + currentDigest());
    }

    private void assertMarkerFailsClosedFor(String markerContent) throws Exception {
        writeRaw(store.markerFor(KEY), markerContent.getBytes(StandardCharsets.UTF_8));
        assertNull(stuckStore().read(KEY).value);
        assertNull(stuckStore().read(KEY).value);
        assertTrue(store.markerFor(KEY).exists());
    }

    @Test
    public void truncatedMarkerFailsClosed() throws Exception {
        assertMarkerFailsClosed(MARKER_HEADER);
    }

    @Test
    public void emptyMarkerFailsClosed() throws Exception {
        assertMarkerFailsClosed("");
    }

    @Test
    public void incompleteDigestFailsClosed() throws Exception {
        assertMarkerFailsClosed(MARKER_HEADER + "a".repeat(63));
    }

    @Test
    public void invalidDigestCharactersFailClosed() throws Exception {
        assertMarkerFailsClosed(MARKER_HEADER + "z".repeat(64));
    }

    @Test
    public void uppercaseOrPaddedDigestFailsClosed() throws Exception {
        assertMarkerFailsClosed(MARKER_HEADER + "A".repeat(64));
        assertMarkerFailsClosedFor(MARKER_HEADER + "a".repeat(64) + "\n");
    }

    @Test
    public void wrongPrefixFailsClosed() throws Exception {
        assertMarkerFailsClosed("revoked-v2:" + "a".repeat(64));
    }

    @Test
    public void unreadableMarkerFailsClosed() throws Exception {
        store.write(KEY, VALUE);
        // Un directorio en lugar de archivo: no se puede leer como marca
        assertTrue(store.markerFor(KEY).mkdirs());
        assertNull(stuckStore().read(KEY).value);
        assertNull(stuckStore().read(KEY).value);
        assertTrue(store.markerFor(KEY).exists());
    }

    @Test
    public void invalidMarkerIsRemovedOnlyAfterRevokedMaterialIsDeleted() throws Exception {
        store.write(KEY, VALUE);
        writeRaw(store.markerFor(KEY), MARKER_HEADER.getBytes(StandardCharsets.UTF_8));

        // Con almacenamiento sano: se borra el material, y solo entonces la marca
        assertNull(store.read(KEY).value);
        assertFalse(store.fileFor(KEY).exists());
        assertFalse(keys.hasKey(SecureFileStore.itemId(KEY)));
        assertFalse(store.markerFor(KEY).exists());
    }

    @Test
    public void newSessionAfterInvalidMarkerOnceMaterialIsDeleted() throws Exception {
        store.write(KEY, VALUE);
        writeRaw(store.markerFor(KEY), MARKER_HEADER.getBytes(StandardCharsets.UTF_8));

        store.write(KEY, "sesión nueva");
        assertEquals("sesión nueva", store.read(KEY).value);
        assertEquals("sesión nueva", reopened().read(KEY).value);
        assertFalse(store.markerFor(KEY).exists());
    }

    @Test
    public void newSessionIsRefusedWhileInvalidMarkerCannotBeResolved() throws Exception {
        store.write(KEY, VALUE);
        writeRaw(store.markerFor(KEY), MARKER_HEADER.getBytes(StandardCharsets.UTF_8));

        SecureFileStore stuck = stuckStore();
        assertThrows(IOException.class, () -> stuck.write(KEY, "sesión nueva"));
        // Ni la anterior ni la nueva quedan disponibles, y la marca sigue
        assertNull(stuckStore().read(KEY).value);
        assertTrue(store.markerFor(KEY).exists());
    }

    @Test
    public void validMarkerIsRemovedOnlyAfterVerifiedNewSession() throws Exception {
        store.write(KEY, VALUE);
        String revoked = currentDigest();
        writeRaw(store.markerFor(KEY), (MARKER_HEADER + revoked).getBytes(StandardCharsets.UTF_8));

        store.write(KEY, "sesión nueva");
        assertEquals("sesión nueva", reopened().read(KEY).value);
        assertFalse(store.markerFor(KEY).exists());
    }

    @Test
    public void tripleFailureIsReportedWithoutGuarantee() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        files.failMarkerWrite = true;
        SecureFileStore failing = new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX), files);

        // Ni marca ni borrado: se informa el fallo en vez de declarar éxito
        assertThrows(StorageInvalidationException.class, () -> failing.delete(KEY));
        assertFalse(store.markerFor(KEY).exists());
    }

    @Test
    public void fileDeleteFailureWithKeyRemovedNeedsNoMarker() throws Exception {
        store.write(KEY, VALUE);
        FaultyStorage.Files files = new FaultyStorage.Files();
        files.failValueDelete = true;
        files.failMarkerWrite = true;
        assertTrue(new SecureFileStore(directory, keys, files).delete(KEY).pendingCleanup);
        // Sin clave, el archivo sobrante no se puede descifrar
        SecureFileStore.ReadResult result = reopened().read(KEY);
        assertNull(result.value);
        assertFalse(store.fileFor(KEY).exists());
    }

    @Test
    public void keyDeleteFailureWithFileRemovedIsInvalidated() throws Exception {
        store.write(KEY, VALUE);
        assertTrue(new SecureFileStore(directory, new FaultyStorage.StickyKeys(TEST_PREFIX)).delete(KEY).pendingCleanup);
        assertNull(reopened().read(KEY).value);
    }

    // --- Datos dañados

    @Test
    public void recoversFromTamperedFile() throws Exception {
        store.write(KEY, VALUE);
        File file = store.fileFor(KEY);
        byte[] data = new StorageFiles().read(file);
        data[data.length - 1] ^= 0x01;
        writeRaw(file, data);

        SecureFileStore.ReadResult result = store.read(KEY);
        assertNull(result.value);
        assertTrue(result.recovered);
        assertFalse(file.exists());
    }

    @Test
    public void recoversFromTruncatedFile() throws Exception {
        store.write(KEY, VALUE);
        writeRaw(store.fileFor(KEY), new byte[] { 1, 2, 3 });
        SecureFileStore.ReadResult result = store.read(KEY);
        assertNull(result.value);
        assertTrue(result.recovered);
    }

    @Test
    public void rejectsFileMovedToAnotherName() throws Exception {
        store.write(KEY, VALUE);
        store.write("otra-clave", "x");
        writeRaw(store.fileFor("otra-clave"), new StorageFiles().read(store.fileFor(KEY)));
        SecureFileStore.ReadResult result = store.read("otra-clave");
        assertNull(result.value);
        assertTrue(result.recovered);
    }

    @Test
    public void clearRemovesFilesMarkersAndKeys() throws Exception {
        store.write(KEY, VALUE);
        store.clear();
        assertFalse(keys.hasKey(SecureFileStore.itemId(KEY)));
        assertNull(store.read(KEY).value);
    }

    @Test
    public void pluginUsesNoBackupDirectory() {
        File dir = SecureSessionStoragePlugin.storageDirectory(context);
        assertEquals(context.getNoBackupFilesDir(), dir.getParentFile());
    }

    private void assertNoTempFiles() {
        File[] all = directory.listFiles();
        if (all == null) return;
        for (File file : all) assertFalse(file.getName().endsWith(".tmp"));
    }

    private static void writeRaw(File file, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }
}
