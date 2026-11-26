package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Operaciones de archivo usadas por SecureFileStore.
 * Están separadas para que las pruebas puedan simular fallos de disco.
 */
public class StorageFiles {

    // Escribe y fuerza el volcado a disco antes de cerrar
    public void writeAndSync(File file, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
    }

    // rename() reemplaza el destino de forma atómica dentro del mismo sistema de archivos
    public boolean rename(File from, File to) {
        return from.renameTo(to);
    }

    public boolean delete(File file) {
        return file.delete();
    }

    public byte[] read(File file) throws IOException {
        long length = file.length();
        if (length > Integer.MAX_VALUE) throw new IOException("Archivo demasiado grande");
        byte[] data = new byte[(int) length];
        try (InputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read < 0) throw new IOException("Lectura incompleta");
                offset += read;
            }
        }
        return data;
    }
}
