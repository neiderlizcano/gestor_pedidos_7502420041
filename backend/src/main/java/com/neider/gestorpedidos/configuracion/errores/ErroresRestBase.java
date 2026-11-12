package com.neider.gestorpedidos.configuracion.errores;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Respuestas comunes de los adaptadores REST, sin valores recibidos ni causas internas. */
public abstract class ErroresRestBase {

    private final String detalleSolicitudInvalida;

    protected ErroresRestBase(String detalleSolicitudInvalida) {
        this.detalleSolicitudInvalida = detalleSolicitudInvalida;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class,
            ServletRequestBindingException.class})
    public ProblemDetail solicitudInvalida() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalleSolicitudInvalida);
    }

    protected ResponseEntity<ProblemDetail> autenticacionRequerida() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Autenticacion requerida."));
    }
}
