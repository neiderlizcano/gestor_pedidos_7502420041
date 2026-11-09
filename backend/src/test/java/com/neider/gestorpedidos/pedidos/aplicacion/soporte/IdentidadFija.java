package com.neider.gestorpedidos.pedidos.aplicacion.soporte;

import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;

/**
 * Identidad simulada que devuelve siempre el usuario configurado.
 */
public final class IdentidadFija implements IdentidadAutenticada {

    private final UUID usuario;
    private int llamadas;

    public IdentidadFija(UUID usuario) {
        this.usuario = usuario;
    }

    @Override
    public UUID usuarioActual() {
        llamadas++;
        return usuario;
    }

    public int llamadas() {
        return llamadas;
    }
}
