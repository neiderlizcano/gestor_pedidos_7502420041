package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import com.neider.gestorpedidos.configuracion.errores.ErroresRestBase;
import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilConflictoException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.PerfilNoEncontradoException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.dominio.UsuarioInvalidoException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores del perfil propio sin UUID, correos ni detalles de posibles conflictos ajenos. */
@RestControllerAdvice(assignableTypes = UsuariosControlador.class)
public class UsuariosErroresHandler extends ErroresRestBase {

    public UsuariosErroresHandler() {
        super("Solicitud de perfil invalida.");
    }

    @ExceptionHandler(UsuarioInvalidoException.class)
    public ProblemDetail datosInvalidos() {
        return solicitudInvalida();
    }

    @ExceptionHandler(PerfilNoEncontradoException.class)
    public ProblemDetail noEncontrado() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Perfil no encontrado.");
    }

    @ExceptionHandler(PerfilConflictoException.class)
    public ProblemDetail conflicto() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "No se pudo crear el perfil.");
    }

    @ExceptionHandler(UsuarioNoAutenticadoException.class)
    public ResponseEntity<ProblemDetail> noAutenticado() {
        return autenticacionRequerida();
    }
}
