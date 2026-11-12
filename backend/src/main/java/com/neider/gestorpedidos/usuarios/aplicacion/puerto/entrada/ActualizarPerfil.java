package com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Puerto de entrada para actualizar el perfil del usuario autenticado.
 */
@FunctionalInterface
public interface ActualizarPerfil {

    /**
     * Actualiza unicamente {@code username} y {@code nombre}.
     *
     * <p>El identificador y el correo se conservan.</p>
     */
    Usuario ejecutar(DatosPerfilComando comando);
}
