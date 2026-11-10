package com.neider.gestorpedidos.usuarios.aplicacion.soporte;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/**
 * Repositorio simulado que evita sobrescribir perfiles existentes y conserva
 * el correo al actualizar.
 */
public final class RepositorioUsuariosEnMemoria implements RepositorioUsuarios {

    private final Map<UUID, Usuario> perfiles = new LinkedHashMap<>();

    @Override
    public Optional<Usuario> crear(Usuario perfil) {
        boolean idExistente = perfiles.containsKey(perfil.id());
        boolean emailExistente = perfiles.values().stream()
                .anyMatch(usuario -> usuario.email().equals(perfil.email()));

        if (idExistente || emailExistente) {
            return Optional.empty();
        }

        perfiles.put(perfil.id(), perfil);
        return Optional.of(perfil);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return Optional.ofNullable(perfiles.get(id));
    }

    @Override
    public Optional<Usuario> actualizar(Usuario perfil) {
        Usuario existente = perfiles.get(perfil.id());
        if (existente == null) {
            return Optional.empty();
        }

        Usuario actualizado = new Usuario(existente.id(), perfil.username(),
                perfil.nombre(), existente.email());
        perfiles.put(actualizado.id(), actualizado);
        return Optional.of(actualizado);
    }

    /**
     * Guarda un perfil ya existente para preparar un escenario de prueba.
     */
    public void sembrar(Usuario perfil) {
        perfiles.put(perfil.id(), perfil);
    }

    public int total() {
        return perfiles.size();
    }
}
