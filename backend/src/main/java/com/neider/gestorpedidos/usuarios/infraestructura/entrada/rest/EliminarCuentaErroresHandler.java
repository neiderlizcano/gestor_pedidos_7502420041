package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import com.neider.gestorpedidos.configuracion.errores.ErroresRestBase;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.EliminacionCuentaException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = EliminarCuentaControlador.class)
public class EliminarCuentaErroresHandler extends ErroresRestBase {
    public EliminarCuentaErroresHandler() {
        super("Solicitud de eliminacion invalida.");
    }

    @ExceptionHandler({UsuarioNoAutenticadoException.class, AuthenticationException.class})
    public ResponseEntity<ProblemDetail> noAutenticado() {
        return autenticacionRequerida();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail prohibido() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Operacion no permitida.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail fallo() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo completar la eliminacion de la cuenta.");
    }

    @ExceptionHandler(EliminacionCuentaException.class)
    public ResponseEntity<ProblemDetail> falloRemoto(EliminacionCuentaException fallo) {
        if (fallo.motivo() == EliminacionCuentaException.Motivo.RECHAZADA) {
            return autenticacionRequerida();
        }
        HttpStatus estado = switch (fallo.motivo()) {
            case CONFLICTO_STORAGE -> HttpStatus.CONFLICT;
            case NO_DISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIEMPO_AGOTADO -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(estado).body(ProblemDetail.forStatusAndDetail(estado,
                "No se pudo confirmar la eliminacion de la cuenta."));
    }
}
