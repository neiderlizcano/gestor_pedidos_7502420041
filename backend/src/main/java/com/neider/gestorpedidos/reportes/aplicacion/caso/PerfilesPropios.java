package com.neider.gestorpedidos.reportes.aplicacion.caso;

import java.util.List;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

final class PerfilesPropios {

    private PerfilesPropios() {
    }

    static List<Usuario> verificar(List<Usuario> perfiles, UUID propietario) {
        if (perfiles.size() > 1 || perfiles.stream().anyMatch(perfil -> !perfil.id().equals(propietario))) {
            throw new IllegalStateException("El reporte no conserva el aislamiento del perfil propio.");
        }
        return List.copyOf(perfiles);
    }
}
