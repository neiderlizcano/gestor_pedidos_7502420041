package com.neider.gestorpedidos.usuarios.aplicacion.error;

/**
 * Indica que no hay una identidad autenticada disponible.
 */
public class UsuarioNoAutenticadoException extends RuntimeException {

    public UsuarioNoAutenticadoException() {
        super("No hay un usuario autenticado para realizar la operacion.");
    }
}
