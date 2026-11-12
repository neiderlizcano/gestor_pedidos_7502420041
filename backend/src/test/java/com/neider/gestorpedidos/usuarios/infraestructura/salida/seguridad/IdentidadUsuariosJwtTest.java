package com.neider.gestorpedidos.usuarios.infraestructura.salida.seguridad;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.usuarios.aplicacion.error.UsuarioNoAutenticadoException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.IdentidadUsuario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class IdentidadUsuariosJwtTest {

    private final IdentidadUsuariosJwt identidad = new IdentidadUsuariosJwt(new UsuarioAutenticado());

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sinContextoNoInventaIdentidades() {
        SecurityContextHolder.clearContext();
        assertThat(identidad.usuarioActual()).isNull();
        assertThatThrownBy(identidad::usuarioRequerido).isInstanceOf(UsuarioNoAutenticadoException.class);
    }

    @Test
    void autenticacionQueNoEsJwtNoEsFuenteDeIdentidad() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("sintetico", null, "USER"));
        assertThat(identidad.usuarioActual()).isNull();
    }

    @Test
    void jwtNoAutenticadoNoEsFuenteDeIdentidad() {
        var autenticacion = autenticacion();
        autenticacion.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
        assertThat(identidad.usuarioActual()).isNull();
    }

    @Test
    void usaElCorreoSuperiorSinMetadatosEditables() {
        SecurityContextHolder.getContext().setAuthentication(autenticacion());
        assertThat(identidad.usuarioRequerido()).isEqualTo(new IdentidadUsuario(ID, EMAIL));
    }

    private static JwtAuthenticationToken autenticacion() {
        // Identidad sintetica ya autenticada para esta prueba unitaria; HTTP prueba el decoder real.
        Jwt jwt = Jwt.withTokenValue("identidad-sintetica-no-es-un-jwt-real").header("alg", "ES256")
                .subject(ID.toString()).claim("email", EMAIL)
                .claim("user_metadata", Map.of("email", "no-confiable@example.com")).build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
