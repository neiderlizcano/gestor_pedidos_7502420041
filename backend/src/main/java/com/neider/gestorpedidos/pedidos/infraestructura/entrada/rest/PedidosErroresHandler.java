package com.neider.gestorpedidos.pedidos.infraestructura.entrada.rest;

import com.neider.gestorpedidos.configuracion.errores.ErroresRestBase;
import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.pedidos.dominio.PedidoInvalidoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores publicos acotados a pedidos, sin valores recibidos, propietarios ni causas JDBC. */
@RestControllerAdvice(assignableTypes = PedidosControlador.class)
public class PedidosErroresHandler extends ErroresRestBase {

    public PedidosErroresHandler() {
        super("Solicitud de pedido invalida.");
    }

    @ExceptionHandler(PedidoInvalidoException.class)
    public ProblemDetail datosInvalidos() {
        return solicitudInvalida();
    }

    @ExceptionHandler(PedidoNoEncontradoException.class)
    public ProblemDetail noEncontrado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Pedido no encontrado.");
    }

    @ExceptionHandler(UsuarioNoAutenticadoException.class)
    public ResponseEntity<ProblemDetail> noAutenticado() {
        return autenticacionRequerida();
    }
}
