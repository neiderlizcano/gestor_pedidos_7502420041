package com.neider.gestorpedidos.usuarios.aplicacion.error;

/** No conserva respuestas remotas, credenciales ni causas del transporte. */
public final class VerificacionCuentaNoDisponibleException extends RuntimeException {
    public VerificacionCuentaNoDisponibleException() {
        super("Verificacion de identidad temporalmente no disponible.");
    }
}
