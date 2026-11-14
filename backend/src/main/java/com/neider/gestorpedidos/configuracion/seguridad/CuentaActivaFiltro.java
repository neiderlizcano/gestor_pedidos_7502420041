package com.neider.gestorpedidos.configuracion.seguridad;

import java.io.IOException;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.CuentaNoAutorizadaException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.VerificacionCuentaNoDisponibleException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** Solo pertenece a la cadena de Spring Security, no al registro de filtros del servlet. */
final class CuentaActivaFiltro extends OncePerRequestFilter {
    private final VerificarCuentaActiva cuentas;
    private final RequestMatcher publico;

    CuentaActivaFiltro(VerificarCuentaActiva cuentas, RequestMatcher publico) {
        this.cuentas = cuentas;
        this.publico = publico;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publico.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var identidad = SecurityContextHolder.getContext().getAuthentication();
        if (identidad instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            try {
                cuentas.verificar(UUID.fromString(jwt.getToken().getSubject()));
            } catch (CuentaNoAutorizadaException fallo) {
                SecurityContextHolder.clearContext();
                response.setHeader("Cache-Control", "no-store");
                new BearerTokenAuthenticationEntryPoint().commence(request, response,
                        new BadCredentialsException("Autenticacion requerida."));
                return;
            } catch (VerificacionCuentaNoDisponibleException fallo) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setHeader("Cache-Control", "no-store");
                response.setContentType("application/problem+json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"title\":\"Service Unavailable\",\"status\":503,"
                        + "\"detail\":\"Verificacion de identidad temporalmente no disponible.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
