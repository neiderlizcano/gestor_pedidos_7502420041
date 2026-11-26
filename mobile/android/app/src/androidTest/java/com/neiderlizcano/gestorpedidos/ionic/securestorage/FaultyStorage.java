package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

// Dobles de prueba que simulan fallos de disco y del Keystore
final class FaultyStorage {

    private FaultyStorage() {}

    static class Files extends StorageFiles {

        boolean failWrite;
        boolean failRename;
        boolean fakeRename;
        boolean failValueDelete;
        boolean failMarkerWrite;
        boolean failMarkerDelete;

        @Override
        public void writeAndSync(File file, byte[] data) throws IOException {
            if (failWrite || (failMarkerWrite && file.getName().contains(".revoked"))) throw new IOException("disco lleno");
            super.writeAndSync(file, data);
        }

        @Override
        public boolean rename(File from, File to) {
            if (failRename) return false;
            // Informa éxito sin hacer nada: solo la verificación posterior lo detecta
            if (fakeRename) return true;
            return super.rename(from, to);
        }

        @Override
        public boolean delete(File file) {
            if (failValueDelete && file.getName().endsWith(".bin")) return false;
            if (failMarkerDelete && file.getName().endsWith(".revoked")) return false;
            return super.delete(file);
        }
    }

    // Keystore que no puede borrar claves
    static class StickyKeys extends KeystoreKeyProvider {

        StickyKeys(String prefix) {
            super(prefix);
        }

        @Override
        public void deleteKey(String itemId) throws GeneralSecurityException {
            throw new GeneralSecurityException("Keystore no disponible");
        }
    }
}
