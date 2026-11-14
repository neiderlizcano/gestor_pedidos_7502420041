package com.neider.gestorpedidos.usuarios.infraestructura.salida.http;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.neider.gestorpedidos.usuarios.aplicacion.error.CuentaNoAutorizadaException;
import com.nimbusds.jwt.SignedJWT;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integracion optativa, solo GET de Auth: sin Spring Boot, JDBC ni creacion/borrado de cuentas.
 * Excluida de la suite normal por el sufijo IT; ejecutar explicitamente solo con autorizacion.
 * Las cuatro variables deben pertenecer al proyecto de pruebas y a una cuenta YA eliminada.
 */
@Isolated("Silencia y restaura logs para no registrar credenciales de la integracion real.")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_URL", matches = "\\S+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_KEY", matches = "\\S+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_JWT", matches = "\\S+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_USER_ID", matches = "\\S+")
class VerificarCuentaActivaHttpIT {

    @Test
    void rechazaJwtVigenteDeCuentaEliminadaCon403UserNotFound() {
        // No Spring: application.yml no protege los logs de este cliente HTTP independiente.
        var logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        var niveles = new HashMap<Logger, Level>();
        for (Logger logger : logging.getLoggerList()) {
            niveles.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }
        try {
            String base = System.getenv("SUPABASE_TEST_URL");
            String clave = System.getenv("SUPABASE_TEST_KEY");
            String token = System.getenv("SUPABASE_TEST_JWT");
            UUID id = UUID.fromString(System.getenv("SUPABASE_TEST_USER_ID"));

            URI destino = URI.create(base);
            assertTrue("https".equals(destino.getScheme()) && destino.getHost() != null
                            && destino.getHost().matches("[a-z0-9]{20}\\.supabase\\.co")
                            && destino.getRawUserInfo() == null && destino.getRawQuery() == null
                            && destino.getRawFragment() == null
                            && (destino.getPort() == -1 || destino.getPort() == 443)
                            && (destino.getRawPath().isEmpty() || "/".equals(destino.getRawPath())),
                    "Se requiere la URL base HTTPS del proyecto Supabase de pruebas.");
            String emisor = "https://" + destino.getHost() + "/auth/v1";

            // Solo precondiciones locales; la validacion criptografica real la hace Supabase.
            // No se imprimen JWT, payload, subject, claves ni excepciones del parser.
            var firmado = SignedJWT.parse(token);
            var claims = firmado.getJWTClaimsSet();
            assertTrue("ES256".equals(firmado.getHeader().getAlgorithm().getName())
                            && emisor.equals(claims.getIssuer())
                            && id.toString().equalsIgnoreCase(claims.getSubject())
                            && claims.getAudience().contains("authenticated")
                            && "authenticated".equals(claims.getStringClaim("role")),
                    "JWT e identidad deben corresponder al proyecto y usuario de pruebas.");
            assertTrue(claims.getExpirationTime() != null
                            && claims.getExpirationTime().toInstant().isAfter(Instant.now().plusSeconds(30))
                            && (claims.getNotBeforeTime() == null
                                || !claims.getNotBeforeTime().toInstant().isAfter(Instant.now())),
                    "Se requiere un JWT vigente, con al menos 30 segundos restantes.");

            var jwt = Jwt.withTokenValue(token).header("alg", "ES256")
                    .subject(id.toString()).issuer(emisor)
                    .expiresAt(claims.getExpirationTime().toInstant()).build();
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
            SecurityContextHolder.setContext(contexto);

            var estado = new AtomicInteger();
            var consultas = new AtomicInteger();
            var usuarioInexistente = new AtomicBoolean();
            var conexiones = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(3))
                            .setSocketTimeout(Timeout.ofSeconds(5)).build())
                    .setMaxConnTotal(1).setMaxConnPerRoute(1).build();
            try (var cliente = HttpClients.custom().setConnectionManager(conexiones)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                            .setResponseTimeout(Timeout.ofSeconds(5)).build())
                    .disableAutomaticRetries().disableRedirectHandling()
                    .disableCookieManagement().disableAuthCaching().disableContentCompression()
                    .addExecInterceptorLast("comprobar-rechazo-auth", (solicitud, scope, chain) -> {
                        var respuesta = chain.proceed(solicitud, scope);
                        consultas.incrementAndGet();
                        estado.set(respuesta.getCode());
                        // El adaptador no lee cuerpos de error 403. Este observador comprueba
                        // el codigo estable de Auth sin guardar ni publicar el mensaje remoto.
                        try {
                            var entidad = respuesta.getEntity();
                            if (respuesta.getCode() == 403 && entidad != null) {
                                byte[] cuerpo = entidad.getContent().readNBytes(4097);
                                if (cuerpo.length <= 4096) {
                                    var error = JsonMapper.builder().build().readTree(cuerpo);
                                    usuarioInexistente.set(error.isObject()
                                            && "user_not_found".equals(error.path("error_code").asText()));
                                }
                            }
                        } catch (Exception ignorado) {
                            // Respuesta inesperada: la asercion falla sin exponer su contenido.
                            usuarioInexistente.set(false);
                        }
                        return respuesta;
                    }).build()) {
                var adaptador = new VerificarCuentaActivaHttp(cliente, emisor, clave);
                boolean rechazada = false;
                try {
                    adaptador.verificar(id);
                } catch (CuentaNoAutorizadaException esperado) {
                    rechazada = true;
                }
                assertTrue(rechazada, "El adaptador debe rechazar la cuenta eliminada.");
                assertTrue(consultas.get() == 1 && estado.get() == 403 && usuarioInexistente.get(),
                        "Se esperaba exactamente un GET con HTTP 403 y error_code user_not_found.");
                assertTrue(claims.getExpirationTime().toInstant().isAfter(Instant.now()),
                        "El JWT expiro durante la prueba; el resultado no acredita este escenario.");
            }
        } catch (Exception fallo) {
            // Nunca encadenar errores de configuracion, JSON, UUID o transporte con datos reales.
            throw new AssertionError("No se pudo completar la integracion Auth; revisar configuracion y conectividad sin publicar secretos.");
        } finally {
            SecurityContextHolder.clearContext();
            niveles.forEach(Logger::setLevel);
        }
    }
}
