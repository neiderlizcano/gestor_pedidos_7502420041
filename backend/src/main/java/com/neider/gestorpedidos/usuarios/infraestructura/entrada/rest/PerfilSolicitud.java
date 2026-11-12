package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.DatosPerfilComando;

import jakarta.validation.constraints.NotBlank;

/** Lista cerrada de datos editables; UUID, correo y otros campos se rechazan. */
public record PerfilSolicitud(@NotBlank String username, @NotBlank String nombre) {

    @JsonAnySetter
    public void rechazarCampoNoEditable(String campo, Object valor) {
        throw new IllegalArgumentException("El perfil solo admite username y nombre.");
    }

    DatosPerfilComando aComando() {
        return new DatosPerfilComando(username, nombre);
    }
}
