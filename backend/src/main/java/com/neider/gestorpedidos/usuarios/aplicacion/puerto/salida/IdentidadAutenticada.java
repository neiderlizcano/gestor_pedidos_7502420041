package com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida;

import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;

/**
 * Fuente confiable de la identidad del usuario autenticado.
 *
 * <p>La implementacion debe obtener el identificador y el correo de la sesion,
 * el token o el contexto de seguridad. Nunca debe tomarlos de los datos
 * enviados por el cliente.</p>
 */
public interface IdentidadAutenticada {

    /**
     * Identidad del usuario autenticado, o {@code null} si no hay sesion.
     */
    IdentidadUsuario usuarioActual();

    /**
     * Identidad del usuario autenticado, exigiendo que exista una sesion.
     *
     * @throws UsuarioNoAutenticadoException si no hay identidad disponible.
     */
    default IdentidadUsuario usuarioRequerido() {
        IdentidadUsuario usuario = usuarioActual();
        if (usuario == null) {
            throw new UsuarioNoAutenticadoException();
        }
        return usuario;
    }
}
