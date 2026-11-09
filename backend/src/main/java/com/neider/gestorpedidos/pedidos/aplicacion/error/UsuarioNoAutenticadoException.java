package com.neider.gestorpedidos.pedidos.aplicacion.error;

/**
 * Indica que no hay una identidad autenticada disponible.
 */
public class UsuarioNoAutenticadoException extends RuntimeException {

    public UsuarioNoAutenticadoException() {
        super("No hay un usuario autenticado para realizar la operacion.");
    }
}
