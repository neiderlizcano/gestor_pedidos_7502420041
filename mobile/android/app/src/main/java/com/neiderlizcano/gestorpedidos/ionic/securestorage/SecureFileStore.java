package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.UnrecoverableKeyException;
import java.util.Arrays;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda cada valor cifrado con AES-GCM en un archivo propio y con una clave propia del Keystore.
 * Formato: versión (1 byte) + IV (12 bytes) + texto cifrado con etiqueta GCM.
 * El nombre lógico se usa como dato autenticado (AAD): un archivo movido a otro nombre no se descifra.
 *
 * Invalidación persistente: antes de borrar un valor se escribe y verifica una marca
 * (archivo .revoked) con el SHA-256 del texto cifrado revocado. read() consulta la marca
 * antes de devolver nada, así que ese texto cifrado no vuelve a entregarse aunque fallen
 * el borrado de la clave y del archivo, incluso tras reiniciar el proceso.
 * Un valor nuevo lleva otro IV y, por tanto, otro resumen: la marca anterior no lo bloquea.
 */
public class SecureFileStore {

    private static final String TAG = "SecureFileStore";
    private static final byte FORMAT_VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int MIN_LENGTH = 1 + IV_LENGTH + TAG_LENGTH_BITS / 8;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String AAD_PREFIX = "gestor-pedidos-session:";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String MARKER_SUFFIX = ".revoked";
    private static final String MARKER_HEADER = "revoked-v1:";
    // Prefijo + SHA-256 en hexadecimal minúscula, sin nada más
    private static final Pattern MARKER_FORMAT = Pattern.compile("\\Arevoked-v1:[0-9a-f]{64}\\z");

    // Resultado de lectura: recovered indica que había datos ilegibles y se descartaron
    public static final class ReadResult {

        public final String value;
        public final boolean recovered;

        ReadResult(String value, boolean recovered) {
            this.value = value;
            this.recovered = recovered;
        }
    }

    // pendingCleanup: el valor ya no se puede restaurar, pero queda limpieza física pendiente
    public static final class DeleteResult {

        public final boolean pendingCleanup;

        DeleteResult(boolean pendingCleanup) {
            this.pendingCleanup = pendingCleanup;
        }
    }

    private final File directory;
    private final KeystoreKeyProvider keys;
    private final StorageFiles files;

    public SecureFileStore(File directory, KeystoreKeyProvider keys) {
        this(directory, keys, new StorageFiles());
    }

    public SecureFileStore(File directory, KeystoreKeyProvider keys, StorageFiles files) {
        this.directory = directory;
        this.keys = keys;
        this.files = files;
    }

    public synchronized ReadResult read(String name) throws IOException, GeneralSecurityException {
        File file = fileFor(name);
        File marker = markerFor(name);

        // 1. La marca se comprueba antes de leer o descifrar nada
        if (marker.exists() && blockedByMarker(marker, file)) {
            finishPendingInvalidation(name);
            return new ReadResult(null, false);
        }
        if (!file.exists()) return new ReadResult(null, false);

        byte[] data = files.read(file);
        if (data.length < MIN_LENGTH || data[0] != FORMAT_VERSION) return discard(name, "formato inválido");

        SecretKey key;
        try {
            key = keys.existingKey(itemId(name));
        } catch (UnrecoverableKeyException e) {
            return discard(name, "clave irrecuperable");
        }
        // Sin clave (borrada en un cierre previo o tras restaurar el dispositivo) no hay sesión
        if (key == null) return discard(name, "clave ausente");

        try {
            return new ReadResult(decrypt(key, name, data), false);
        } catch (KeyPermanentlyInvalidatedException e) {
            return discard(name, "clave invalidada");
        } catch (GeneralSecurityException e) {
            // Etiqueta GCM incorrecta: archivo alterado o cifrado con otra clave
            return discard(name, "verificación fallida");
        }
    }

    /**
     * Escribe y verifica. También es la sustitución controlada de un valor revocado:
     * el nuevo texto cifrado tiene otro resumen, y solo se confirma si read() lo devuelve.
     */
    public synchronized void write(String name, String value) throws IOException, GeneralSecurityException {
        ensureDirectory();
        File marker = markerFor(name);
        // Una marca inválida bloquearía cualquier valor: antes hay que borrar el material revocado.
        // Si no se puede, no se escribe nada (la marca se conserva).
        if (marker.exists() && readMarker(marker) == null) {
            finishPendingInvalidation(name);
            if (marker.exists()) throw new IOException("Invalidación pendiente sin resolver");
        }

        String id = itemId(name);
        byte[] encoded = encrypt(keys.getOrCreateKey(id), name, value);
        writeVerified(fileFor(name), encoded);

        // Confirmación por el mismo camino que usará el SDK, incluida la comprobación de la marca
        if (!value.equals(read(name).value)) throw new IOException("La escritura no se pudo verificar");
        // Solo con la sesión nueva confirmada se retira una marca válida de un valor anterior
        if (marker.exists()) deleteQuietly(marker);
    }

    /**
     * Invalida y borra un valor.
     * - Resuelve sin limpieza pendiente si clave y archivo se borraron.
     * - Resuelve con pendingCleanup si el valor ya no puede restaurarse (marca confirmada,
     *   o clave o archivo borrados) pero queda algo por borrar; se reintenta al leer o escribir.
     * - Lanza StorageInvalidationException si no se confirmó nada: no hay garantía.
     */
    public synchronized DeleteResult delete(String name) throws IOException {
        File file = fileFor(name);
        File marker = markerFor(name);

        boolean markerConfirmed = false;
        if (file.exists()) {
            try {
                ensureDirectory();
                markerConfirmed = writeMarker(marker, digest(files.read(file)));
            } catch (IOException e) {
                markerConfirmed = false;
            }
        }

        boolean keyGone = deleteKeyConfirmed(itemId(name));
        boolean fileGone = deleteConfirmed(file);

        if (keyGone && fileGone) {
            deleteQuietly(marker);
            return new DeleteResult(false);
        }
        if (markerConfirmed || keyGone || fileGone) {
            Log.w(TAG, "Valor invalidado; limpieza pendiente (marca: " + markerConfirmed + ", clave: " + keyGone + ", archivo: " + fileGone + ")");
            return new DeleteResult(true);
        }
        Log.w(TAG, "No se confirmó la marca ni el borrado");
        throw new StorageInvalidationException();
    }

    // Borra todos los valores, marcas y claves
    public synchronized void clear() throws IOException, GeneralSecurityException {
        File[] all = directory.listFiles();
        if (all != null) {
            for (File file : all) {
                if (!deleteConfirmed(file)) throw new IOException("No se pudo borrar un archivo de sesión");
            }
        }
        keys.deleteAllKeys();
    }

    File fileFor(String name) {
        return new File(directory, itemId(name) + ".bin");
    }

    File markerFor(String name) {
        return new File(directory, itemId(name) + MARKER_SUFFIX);
    }

    // Completa un borrado pendiente; la marca solo se retira cuando clave y archivo ya no existen
    private void finishPendingInvalidation(String name) {
        boolean keyGone = deleteKeyConfirmed(itemId(name));
        boolean fileGone = deleteConfirmed(fileFor(name));
        if (keyGone && fileGone) deleteQuietly(markerFor(name));
    }

    private ReadResult discard(String name, String reason) {
        Log.w(TAG, "Datos de sesión descartados: " + reason);
        try {
            delete(name);
        } catch (IOException e) {
            Log.w(TAG, "No se pudo completar el descarte");
        }
        return new ReadResult(null, true);
    }

    private boolean writeMarker(File marker, String revokedDigest) throws IOException {
        byte[] content = (MARKER_HEADER + revokedDigest).getBytes(StandardCharsets.UTF_8);
        writeVerified(marker, content);
        return revokedDigest.equals(readMarker(marker));
    }

    /**
     * true si la marca impide entregar el archivo actual.
     * Falla cerrado: marca inválida o ilegible, archivo ausente o ilegible, o resumen coincidente.
     * Solo una marca válida con un resumen distinto (de un valor ya sustituido) deja pasar.
     * Nunca retira la marca aquí.
     */
    private boolean blockedByMarker(File marker, File file) {
        String revokedDigest = readMarker(marker);
        if (revokedDigest == null || !file.exists()) return true;
        try {
            return revokedDigest.equals(digest(files.read(file)));
        } catch (IOException e) {
            return true;
        }
    }

    // Devuelve el resumen revocado solo si la marca tiene exactamente el formato esperado
    private String readMarker(File marker) {
        try {
            String content = new String(files.read(marker), StandardCharsets.UTF_8);
            return MARKER_FORMAT.matcher(content).matches() ? content.substring(MARKER_HEADER.length()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    // Archivo temporal con fsync, renombrado y relectura byte a byte
    private void writeVerified(File target, byte[] content) throws IOException {
        File temp = new File(directory, target.getName() + TEMP_SUFFIX);
        try {
            files.writeAndSync(temp, content);
            if (!files.rename(temp, target)) throw new IOException("No se pudo reemplazar el archivo");
        } finally {
            if (temp.exists()) files.delete(temp);
        }
        if (!Arrays.equals(files.read(target), content)) throw new IOException("Contenido distinto al escrito");
    }

    private boolean deleteKeyConfirmed(String id) {
        try {
            keys.deleteKey(id);
            return !keys.hasKey(id);
        } catch (GeneralSecurityException | IOException e) {
            return false;
        }
    }

    private boolean deleteConfirmed(File file) {
        if (!file.exists()) return true;
        return files.delete(file) && !file.exists();
    }

    private void deleteQuietly(File file) {
        if (!deleteConfirmed(file)) Log.w(TAG, "No se pudo retirar un archivo auxiliar");
    }

    private byte[] encrypt(SecretKey key, String name, String value) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.updateAAD(aad(name));
        byte[] plain = value.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try {
            encrypted = cipher.doFinal(plain);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_LENGTH) throw new GeneralSecurityException("IV inesperado");
        return ByteBuffer.allocate(1 + IV_LENGTH + encrypted.length).put(FORMAT_VERSION).put(iv).put(encrypted).array();
    }

    private static String decrypt(SecretKey key, String name, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, data, 1, IV_LENGTH));
        cipher.updateAAD(aad(name));
        byte[] plain = cipher.doFinal(data, 1 + IV_LENGTH, data.length - 1 - IV_LENGTH);
        try {
            return new String(plain, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private void ensureDirectory() throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("No se pudo crear el directorio de sesión");
        }
    }

    private static byte[] aad(String name) {
        return (AAD_PREFIX + name).getBytes(StandardCharsets.UTF_8);
    }

    static String digest(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // Identificador estable por nombre lógico, usado para archivos y alias de clave
    static String itemId(String name) {
        return digest(name.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
