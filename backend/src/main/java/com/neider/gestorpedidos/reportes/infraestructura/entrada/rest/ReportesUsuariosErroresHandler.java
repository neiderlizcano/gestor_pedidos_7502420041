package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import com.neider.gestorpedidos.configuracion.errores.ErroresRestBase;
import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.error.UsuarioNoAutenticadoException;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReportesUsuariosControlador.class)
public class ReportesUsuariosErroresHandler extends ErroresRestBase {

    public ReportesUsuariosErroresHandler() {
        super("Solicitud de reporte invalida.");
    }

    @ExceptionHandler(ConsultaReporteInvalidaException.class)
    public ProblemDetail datosInvalidos() {
        return solicitudInvalida();
    }

    @ExceptionHandler(UsuarioNoAutenticadoException.class)
    public ResponseEntity<ProblemDetail> noAutenticado() {
        return autenticacionRequerida();
    }
}
