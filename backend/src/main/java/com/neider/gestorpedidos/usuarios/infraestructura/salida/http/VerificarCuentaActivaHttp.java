package com.neider.gestorpedidos.usuarios.infraestructura.salida.http;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.CuentaNoAutorizadaException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.VerificacionCuentaNoDisponibleException;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Consulta Auth con el JWT de la solicitud ya validado; nunca con privilegios de administrador. */
public final class VerificarCuentaActivaHttp implements VerificarCuentaActiva {
    private static final int MAX_RESPUESTA = 65536;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final CloseableHttpClient cliente;
    private final URI destino;
    private final String publishableKey;

    public VerificarCuentaActivaHttp(CloseableHttpClient cliente, String emisor, String publishableKey) {
        this.cliente = Objects.requireNonNull(cliente);
        try {
            URI issuer = URI.create(Objects.requireNonNull(emisor));
            if (!"https".equals(issuer.getScheme()) || issuer.getHost() == null
                    || issuer.getRawUserInfo() != null || issuer.getRawQuery() != null
                    || issuer.getRawFragment() != null || (issuer.getPort() != -1 && issuer.getPort() != 443)
                    || !"/auth/v1".equals(issuer.getRawPath())) {
                throw new IllegalArgumentException();
            }
            destino = URI.create(emisor + "/user");
        } catch (RuntimeException fallo) {
            throw new IllegalArgumentException("Se requiere un emisor HTTPS fijo de Supabase Auth.");
        }
        if (publishableKey == null || !publishableKey.matches("sb_publishable_[A-Za-z0-9_-]{1,1024}")) {
            throw new IllegalArgumentException("Se requiere una clave publishable de Supabase Auth.");
        }
        this.publishableKey = publishableKey;
    }

    @Override
    public void verificar(UUID autenticado) {
        var identidad = SecurityContextHolder.getContext().getAuthentication();
        if (autenticado == null || !(identidad instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated() || !autenticado.toString().equalsIgnoreCase(jwt.getToken().getSubject())) {
            throw new CuentaNoAutorizadaException();
        }
        var solicitud = new HttpGet(destino);
        solicitud.setHeader("Authorization", "Bearer " + jwt.getToken().getTokenValue());
        solicitud.setHeader("apikey", publishableKey);
        solicitud.setHeader("Accept", "application/json");
        // Sin redirecciones, reintentos, cache ni almacenamiento del token fuera de esta llamada.
        try (var respuesta = cliente.executeOpen(null, solicitud, HttpClientContext.create())) {
            int estado = respuesta.getCode();
            if (estado == 401 || estado == 403) {
                throw new CuentaNoAutorizadaException();
            }
            if (estado != 200 && estado != 404) {
                throw new VerificacionCuentaNoDisponibleException();
            }
            var entidad = respuesta.getEntity();
            if (entidad == null || entidad.getContentType() == null
                    || !"application/json".equalsIgnoreCase(entidad.getContentType().split(";", 2)[0].trim())) {
                throw new VerificacionCuentaNoDisponibleException();
            }
            byte[] cuerpo = entidad.getContent().readNBytes(MAX_RESPUESTA + 1);
            if (cuerpo.length > MAX_RESPUESTA) {
                throw new VerificacionCuentaNoDisponibleException();
            }
            var usuario = JSON.readTree(cuerpo);
            if (estado == 404) {
                if (usuario.isObject() && "user_not_found".equals(usuario.path("code").asText())) {
                    throw new CuentaNoAutorizadaException();
                }
                // Un 404 de proxy/ruta incorrecta no confirma que la cuenta haya desaparecido.
                throw new VerificacionCuentaNoDisponibleException();
            }
            if (!usuario.isObject() || !usuario.path("id").isString()) {
                throw new VerificacionCuentaNoDisponibleException();
            }
            if (!autenticado.toString().equalsIgnoreCase(usuario.path("id").asText())) {
                throw new CuentaNoAutorizadaException();
            }
        } catch (IOException fallo) {
            throw new VerificacionCuentaNoDisponibleException();
        } catch (CuentaNoAutorizadaException | VerificacionCuentaNoDisponibleException fallo) {
            throw fallo;
        } catch (RuntimeException fallo) {
            // Incluye JSON invalido: nunca propagar cuerpo remoto ni detalles del cliente HTTP.
            throw new VerificacionCuentaNoDisponibleException();
        } finally {
            solicitud.removeHeaders("Authorization");
            solicitud.removeHeaders("apikey");
        }
    }
}
