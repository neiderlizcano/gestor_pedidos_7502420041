package com.neider.gestorpedidos.usuarios.aplicacion.error;

/**
 * Indica que el perfil del usuario autenticado no existe.
 */
public class PerfilNoEncontradoException extends RuntimeException {

    public PerfilNoEncontradoException() {
        super("No existe el perfil del usuario autenticado.");
    }
}
