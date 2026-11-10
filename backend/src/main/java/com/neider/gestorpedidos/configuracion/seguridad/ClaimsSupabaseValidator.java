package com.neider.gestorpedidos.configuracion.seguridad;

import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Claims obligatorios adicionales a firma, emisor y ventanas temporales.
 */
final class ClaimsSupabaseValidator implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getExpiresAt() == null
                || jwt.getAudience() == null || !jwt.getAudience().contains("authenticated")
                || !uuidValido(jwt.getSubject())
                || !"authenticated".equals(jwt.getClaim("role"))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El JWT no representa una identidad de usuario valida.", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean uuidValido(String subject) {
        if (subject == null) {
            return false;
        }
        try {
            // UUID.fromString tambien acepta formas abreviadas; exigir formato canonico.
            return UUID.fromString(subject).toString().equalsIgnoreCase(subject);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
