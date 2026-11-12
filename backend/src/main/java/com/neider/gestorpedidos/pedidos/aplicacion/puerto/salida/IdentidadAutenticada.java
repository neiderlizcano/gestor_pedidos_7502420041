package com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida;

import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;

/**
 * Fuente confiable de la identidad del usuario autenticado.
 *
 * <p>La implementacion debe obtener el identificador de la sesion, el token o
 * el contexto de seguridad. Nunca debe tomarlo de los datos enviados por el
 * cliente.</p>
 */
public interface IdentidadAutenticada {

    /**
     * Identificador del usuario autenticado, o {@code null} si no hay sesion.
     */
    UUID usuarioActual();

    /**
     * Identificador del usuario autenticado, exigiendo que exista una sesion.
     *
     * @throws UsuarioNoAutenticadoException si no hay identidad disponible.
     */
    default UUID usuarioRequerido() {
        UUID usuario = usuarioActual();
        if (usuario == null) {
            throw new UsuarioNoAutenticadoException();
        }
        return usuario;
    }
}
