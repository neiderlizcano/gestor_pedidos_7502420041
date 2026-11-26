package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Claves AES-256 del Android Keystore, una por elemento guardado.
 * Borrar la clave de un elemento lo deja indescifrable aunque su archivo siga en disco.
 */
public class KeystoreKeyProvider {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private final String aliasPrefix;

    public KeystoreKeyProvider(String aliasPrefix) {
        this.aliasPrefix = aliasPrefix;
    }

    String aliasFor(String itemId) {
        return aliasPrefix + itemId;
    }

    // Devuelve la clave existente o null si no hay ninguna
    public SecretKey existingKey(String itemId) throws GeneralSecurityException, IOException {
        KeyStore.Entry entry = loadKeyStore().getEntry(aliasFor(itemId), null);
        return entry instanceof KeyStore.SecretKeyEntry ? ((KeyStore.SecretKeyEntry) entry).getSecretKey() : null;
    }

    public SecretKey getOrCreateKey(String itemId) throws GeneralSecurityException, IOException {
        SecretKey key = existingKey(itemId);
        if (key != null) return key;

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(
            new KeyGenParameterSpec.Builder(aliasFor(itemId), KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // El Keystore genera un IV nuevo en cada cifrado
                .setRandomizedEncryptionRequired(true)
                .build()
        );
        return generator.generateKey();
    }

    // Borra la clave y confirma que ya no existe
    public void deleteKey(String itemId) throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore();
        String alias = aliasFor(itemId);
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias);
        if (loadKeyStore().containsAlias(alias)) throw new GeneralSecurityException("La clave sigue en el Keystore");
    }

    public boolean hasKey(String itemId) throws GeneralSecurityException, IOException {
        return loadKeyStore().containsAlias(aliasFor(itemId));
    }

    public void deleteAllKeys() throws GeneralSecurityException, IOException {
        KeyStore keyStore = loadKeyStore();
        List<String> aliases = new ArrayList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            if (alias.startsWith(aliasPrefix)) aliases.add(alias);
        }
        for (String alias : aliases) keyStore.deleteEntry(alias);
    }

    private static KeyStore loadKeyStore() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        return keyStore;
    }
}
