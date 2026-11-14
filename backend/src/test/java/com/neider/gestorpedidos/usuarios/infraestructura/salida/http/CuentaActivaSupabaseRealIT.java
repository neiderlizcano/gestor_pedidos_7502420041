package com.neider.gestorpedidos.usuarios.infraestructura.salida.http;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
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

/**
 * Integracion optativa de cuenta activa: un GET de Auth, sin Spring Boot ni JDBC.
 * Ejecutar explicitamente y solo con autorizacion, usando una cuenta ficticia activa.
 * El sufijo IT la excluye de la suite normal de Surefire.
 */
@Isolated("Silencia y restaura logs para no registrar credenciales de la integracion real.")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_URL", matches = "\\S+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_KEY", matches = "\\S+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_JWT", matches = "\\S+")
@EnabledIfEnvironmentVariable(named = "SUPABASE_TEST_USER_ID", matches = "\\S+")
class CuentaActivaSupabaseRealIT {

    @Test
    void permiteCuentaActivaConIdentidadEsperada() {
        // Sin Spring, application.yml no silencia el cliente HTTP independiente.
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

            // Precondiciones locales, sin imprimir el payload. Supabase verifica la firma real.
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
            // Mismo transporte, pool, timeouts y restricciones que CuentaActivaHttpConfiguracion.
            var conexiones = PoolingHttpClientConnectionManagerBuilder.create()
                    .setDefaultTlsConfig(TlsConfig.custom().setHandshakeTimeout(Timeout.ofSeconds(3)).build())
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(Timeout.ofSeconds(3))
                            .setSocketTimeout(Timeout.ofSeconds(5)).build())
                    .setMaxConnTotal(10).setMaxConnPerRoute(10).build();
            try (var cliente = HttpClients.custom().setConnectionManager(conexiones)
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                            .setResponseTimeout(Timeout.ofSeconds(5)).build())
                    .disableAutomaticRetries().disableRedirectHandling()
                    .disableCookieManagement().disableAuthCaching().disableContentCompression()
                    .addExecInterceptorLast("comprobar-cuenta-activa", (solicitud, scope, chain) -> {
                        var respuesta = chain.proceed(solicitud, scope);
                        consultas.incrementAndGet();
                        estado.set(respuesta.getCode());
                        // No inspeccionar ni copiar el cuerpo con datos personales o cabeceras.
                        return respuesta;
                    }).build()) {
                var adaptador = new VerificarCuentaActivaHttp(cliente, emisor, clave);
                // El adaptador solo retorna si el id de la respuesta coincide con la identidad esperada.
                // Cualquier excepcion hace fallar esta prueba sin publicar su causa.
                adaptador.verificar(id);
                assertTrue(consultas.get() == 1 && estado.get() == 200,
                        "Se esperaba exactamente un GET con HTTP 200 y la identidad esperada.");
                assertTrue(claims.getExpirationTime().toInstant().isAfter(Instant.now()),
                        "El JWT expiro durante la prueba; repetir con credenciales vigentes.");
            }
        } catch (Exception fallo) {
            // No encadenar excepciones de transporte, parser o configuracion con datos reales.
            throw new AssertionError("No se pudo verificar la cuenta activa; revisar configuracion y conectividad sin publicar secretos.");
        } finally {
            SecurityContextHolder.clearContext();
            niveles.forEach(Logger::setLevel);
        }
    }
}
