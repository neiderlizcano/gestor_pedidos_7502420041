package com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida;

import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;

/**
 * Identificador y correo del usuario autenticado.
 *
 * <p>El correo forma parte de la identidad administrada por la autenticacion y
 * no puede editarse desde el perfil.</p>
 */
public record IdentidadUsuario(UUID id, String email) {

    public IdentidadUsuario {
        if (id == null || email == null || email.isBlank()) {
            throw new UsuarioNoAutenticadoException();
        }
    }
}
