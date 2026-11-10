package com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Puerto de entrada para consultar el perfil del usuario autenticado.
 */
@FunctionalInterface
public interface ConsultarPerfilPropio {

    /**
     * Devuelve el perfil del usuario autenticado.
     */
    Usuario ejecutar();
}
