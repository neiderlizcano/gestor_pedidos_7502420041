package com.neider.gestorpedidos.reportes.aplicacion.puerto.salida;

import java.util.UUID;

import com.neider.gestorpedidos.reportes.aplicacion.error.UsuarioNoAutenticadoException;

@FunctionalInterface
public interface IdentidadAutenticada {

    UUID usuarioActual();

    default UUID usuarioRequerido() {
        UUID usuario = usuarioActual();
        if (usuario == null) {
            throw new UsuarioNoAutenticadoException();
        }
        return usuario;
    }
}
