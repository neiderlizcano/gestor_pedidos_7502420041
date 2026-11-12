package com.neider.gestorpedidos.usuarios.infraestructura.salida.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.EliminacionCuentaException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.EliminacionCuentaException.Motivo;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;

/** Solo transporte: el caso de uso no conoce JWT, HTTP ni Supabase. */
public final class EliminarCuentaHttp implements EliminarCuenta {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final CloseableHttpClient cliente;
    private final URI destino;
    private final String publishableKey;

    public EliminarCuentaHttp(CloseableHttpClient cliente, String url, String publishableKey) {
        this.cliente = Objects.requireNonNull(cliente);
        try {
            destino = URI.create(Objects.requireNonNull(url));
            if (!"https".equals(destino.getScheme()) || destino.getHost() == null
                    || destino.getRawUserInfo() != null || destino.getRawQuery() != null
                    || destino.getRawFragment() != null
                    || (destino.getPort() != -1 && destino.getPort() != 443)
                    || !"/functions/v1/delete-my-account".equals(destino.getRawPath())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException fallo) {
            // Nunca incorporar URL/credenciales recibidas a la excepcion.
            throw new IllegalArgumentException("Se requiere una URL HTTPS fija de delete-my-account.");
        }
        this.publishableKey = Objects.requireNonNull(publishableKey);
        if (!publishableKey.isEmpty() && !publishableKey.matches("sb_publishable_[A-Za-z0-9_-]{1,1024}")) {
            throw new IllegalArgumentException("Solo se admite una clave publishable opcional.");
        }
    }

    @Override
    public void eliminar(UUID autenticado) {
        Objects.requireNonNull(autenticado);
        var identidad = SecurityContextHolder.getContext().getAuthentication();
        if (!(identidad instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Se requiere autenticacion JWT.");
        }
        if (!autenticado.toString().equalsIgnoreCase(jwt.getToken().getSubject())) {
            throw new AccessDeniedException("La identidad no coincide.");
        }
        var solicitud = new HttpDelete(destino);
        solicitud.setHeader("Authorization", "Bearer " + jwt.getToken().getTokenValue());
        solicitud.setHeader("Accept", "application/json");
        if (!publishableKey.isEmpty()) {
            solicitud.setHeader("apikey", publishableKey);
        }
        // Sin entidad, identidad en URL, redirecciones ni reintentos. No se conserva el JWT.
        try (ClassicHttpResponse respuesta = cliente.executeOpen(null, solicitud, HttpClientContext.create())) {
            int estado = respuesta.getCode();
            switch (estado) {
                case 204 -> { return; }
                case 401 -> throw new EliminacionCuentaException(Motivo.RECHAZADA);
                case 409 -> throw new EliminacionCuentaException(conflictoStorage(respuesta)
                        ? Motivo.CONFLICTO_STORAGE : Motivo.SERVICIO_REMOTO);
                case 429, 503 -> throw new EliminacionCuentaException(Motivo.NO_DISPONIBLE);
                case 504 -> throw new EliminacionCuentaException(Motivo.TIEMPO_AGOTADO);
                default -> throw new EliminacionCuentaException(Motivo.SERVICIO_REMOTO);
            }
        } catch (IOException fallo) {
            throw new EliminacionCuentaException(fallo instanceof InterruptedIOException
                    ? Motivo.TIEMPO_AGOTADO : Motivo.NO_DISPONIBLE);
        } finally {
            solicitud.removeHeaders("Authorization");
        }
    }

    private static boolean conflictoStorage(ClassicHttpResponse respuesta) throws IOException {
        // Contrato propio estricto; nunca clasificar por mensajes libres de Auth/SQL.
        var entidad = respuesta.getEntity();
        if (entidad == null || entidad.getContentType() == null
                || !entidad.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            return false;
        }
        byte[] bytes = entidad.getContent().readNBytes(1025);
        if (bytes.length > 1024) {
            return false;
        }
        try {
            var json = JSON.readTree(bytes);
            return json.isObject() && json.size() == 1 && "STORAGE_CONFLICT".equals(json.path("code").asText());
        } catch (RuntimeException fallo) {
            return false;
        }
    }
}
