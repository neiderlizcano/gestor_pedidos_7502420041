package com.neider.gestorpedidos.usuarios.dominio;

/**
 * Indica que los datos de un perfil no cumplen las reglas del dominio.
 */
public class UsuarioInvalidoException extends RuntimeException {

    public UsuarioInvalidoException(String mensaje) {
        super(mensaje);
    }
}
