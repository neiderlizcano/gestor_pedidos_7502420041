package com.neider.gestorpedidos.usuarios.aplicacion.soporte;

import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;

/**
 * Identidad simulada que devuelve siempre el usuario configurado.
 */
public final class IdentidadFija implements IdentidadAutenticada {

    private final IdentidadUsuario usuario;
    private int llamadas;

    public IdentidadFija(IdentidadUsuario usuario) {
        this.usuario = usuario;
    }

    public IdentidadFija(UUID id, String email) {
        this(new IdentidadUsuario(id, email));
    }

    @Override
    public IdentidadUsuario usuarioActual() {
        llamadas++;
        return usuario;
    }

    public int llamadas() {
        return llamadas;
    }
}
