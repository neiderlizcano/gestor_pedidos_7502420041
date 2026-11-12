package com.neider.gestorpedidos.reportes.aplicacion.error;

public class UsuarioNoAutenticadoException extends RuntimeException {

    public UsuarioNoAutenticadoException() {
        super("No hay un usuario autenticado para consultar reportes.");
    }
}
