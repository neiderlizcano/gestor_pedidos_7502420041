package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import java.util.UUID;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/** Datos del perfil propio, sin credenciales, roles ni metadatos de Auth. */
public record PerfilRespuesta(UUID id, String username, String nombre, String email) {

    static PerfilRespuesta desde(Usuario usuario) {
        return new PerfilRespuesta(usuario.id(), usuario.username(), usuario.nombre(), usuario.email());
    }
}
