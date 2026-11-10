package com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida;

import java.util.Optional;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Contrato de persistencia de perfiles de usuario.
 *
 * <p>Todas las operaciones usan el identificador del usuario autenticado como
 * clave del perfil, de modo que un usuario solo puede operar sobre el suyo.</p>
 */
public interface RepositorioUsuarios {

    /**
     * Crea el perfil solo si no existe otro con el mismo identificador o
     * correo. Nunca sobrescribe un perfil existente.
     *
     * @return el perfil creado, o vacio si hay conflicto.
     */
    Optional<Usuario> crear(Usuario perfil);

    /**
     * Busca el perfil por el identificador del usuario autenticado.
     */
    Optional<Usuario> buscarPorId(UUID id);

    /**
     * Actualiza {@code username} y {@code nombre} del perfil indicado.
     *
     * <p>El correo del perfil existente no se modifica.</p>
     *
     * @return el perfil actualizado, o vacio si no existe.
     */
    Optional<Usuario> actualizar(Usuario perfil);
}
