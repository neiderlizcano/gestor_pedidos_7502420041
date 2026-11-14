package com.neider.gestorpedidos.usuarios.aplicacion.error;

public final class CuentaNoAutorizadaException extends RuntimeException {
    public CuentaNoAutorizadaException() {
        super("Autenticacion requerida.");
    }
}
