package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import java.io.IOException;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.entrada.EliminarCuentaPropia;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistencia")
public class EliminarCuentaControlador {
    private final EliminarCuentaPropia eliminar;

    public EliminarCuentaControlador(EliminarCuentaPropia eliminar) {
        this.eliminar = eliminar;
    }

    @DeleteMapping("/api/v1/usuarios/me")
    public ResponseEntity<Void> eliminar(HttpServletRequest solicitud)
            throws IOException, ServletRequestBindingException {
        // No hay representacion de entrada: rechazar Content-Type tambien cubre
        // formularios consumidos por filtros, sin longitud ni Transfer-Encoding (HTTP/2).
        if (solicitud.getQueryString() != null || solicitud.getContentLengthLong() > 0
                || solicitud.getContentType() != null || solicitud.getHeader("Transfer-Encoding") != null
                || !solicitud.getParameterMap().isEmpty()
                || solicitud.getInputStream().read() != -1) {
            throw new ServletRequestBindingException("La eliminacion no admite cuerpo ni parametros.");
        }
        eliminar.ejecutar();
        return ResponseEntity.noContent().build();
    }
}
