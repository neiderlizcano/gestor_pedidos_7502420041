package com.neider.gestorpedidos.usuarios.aplicacion.error;

/** Fallos del puerto de eliminacion, sin tipos HTTP ni causas sensibles. */
public final class EliminacionCuentaException extends RuntimeException {
    public enum Motivo {
        RECHAZADA, CONFLICTO_STORAGE, SERVICIO_REMOTO, NO_DISPONIBLE, TIEMPO_AGOTADO
    }

    private final Motivo motivo;

    public EliminacionCuentaException(Motivo motivo) {
        super("No se pudo confirmar la eliminacion de la cuenta.");
        this.motivo = java.util.Objects.requireNonNull(motivo);
    }

    public Motivo motivo() {
        return motivo;
    }
}
