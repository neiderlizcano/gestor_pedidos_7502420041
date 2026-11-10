package com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Puerto de entrada para crear el perfil del usuario autenticado.
 */
@FunctionalInterface
public interface CrearPerfil {

    /**
     * Crea el perfil con los datos indicados.
     *
     * <p>El identificador y el correo provienen del puerto de identidad.</p>
     */
    Usuario ejecutar(DatosPerfilComando comando);
}
