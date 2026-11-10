package com.neider.gestorpedidos.usuarios.aplicacion.error;

/**
 * Indica que no se puede crear el perfil porque ya existe otro con el mismo
 * identificador o correo.
 */
public class PerfilConflictoException extends RuntimeException {

    public PerfilConflictoException() {
        super("Ya existe un perfil con el mismo identificador o correo.");
    }
}
