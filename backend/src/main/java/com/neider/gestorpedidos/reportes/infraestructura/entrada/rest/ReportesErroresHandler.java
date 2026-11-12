package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import com.neider.gestorpedidos.configuracion.errores.ErroresRestBase;
import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.error.UsuarioNoAutenticadoException;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReportesPedidosControlador.class)
public class ReportesErroresHandler extends ErroresRestBase {

    public ReportesErroresHandler() {
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
