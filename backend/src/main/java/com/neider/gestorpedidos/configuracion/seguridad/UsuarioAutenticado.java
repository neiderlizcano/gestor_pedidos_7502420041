package com.neider.gestorpedidos.configuracion.seguridad;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Acceso al UUID del JWT ya validado para futuros adaptadores de identidad.
 * No implementa ni modifica los puertos de los modulos de aplicacion.
 */
@Component
public final class UsuarioAutenticado {

    /**
     * Devuelve el UUID autenticado o null si no existe autenticacion JWT.
     */
    public UUID usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        return null;
    }
}
