package com.neiderlizcano.gestorpedidos.ionic.securestorage;

import java.io.IOException;

/**
 * No se pudo confirmar la marca de invalidación ni el borrado de la clave o del archivo.
 * En ese caso no hay garantía de que el valor anterior deje de poder leerse.
 */
public class StorageInvalidationException extends IOException {

    public StorageInvalidationException() {
        super("No se confirmó la invalidación");
    }
}
