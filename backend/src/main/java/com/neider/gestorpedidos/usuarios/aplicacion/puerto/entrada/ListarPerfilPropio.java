package com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada;

import java.util.List;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Puerto de entrada para listar el perfil del usuario autenticado.
 */
@FunctionalInterface
public interface ListarPerfilPropio {

    /**
     * Devuelve una lista con cero o un elemento: el perfil del usuario
     * autenticado, si existe.
     */
    List<Usuario> ejecutar();
}
