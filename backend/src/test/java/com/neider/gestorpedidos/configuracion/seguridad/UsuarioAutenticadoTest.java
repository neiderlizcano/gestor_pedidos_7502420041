package com.neider.gestorpedidos.configuracion.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UsuarioAutenticadoTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UsuarioAutenticado usuario = new UsuarioAutenticado();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void obtieneUuidDelJwtAutenticado() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt(), List.of()));

        assertThat(usuario.usuarioActual()).isEqualTo(ID);
    }

    @Test
    void sinAutenticacionDevuelveNull() {
        assertThat(usuario.usuarioActual()).isNull();
    }

    @Test
    void ignoraAutenticacionAnonima() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "prueba", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(usuario.usuarioActual()).isNull();
    }

    @Test
    void ignoraJwtNoAutenticado() {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt()));

        assertThat(usuario.usuarioActual()).isNull();
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("sintetico").header("alg", "ES256").subject(ID.toString()).build();
    }
}
