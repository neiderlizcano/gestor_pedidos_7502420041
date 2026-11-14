package com.neider.gestorpedidos.configuracion.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP y firmas reales, JWKS local, filtro y adaptador reales; transporte de Auth simulado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SeguridadJwtTests.EndpointsPrueba.class)
class SeguridadJwtTests {

    private static final String EMISOR = "https://supabase.invalid/auth/v1";
    private static final String SUBJECT = "11111111-1111-1111-1111-111111111111";
    private static final ECKey FIRMA = claveEc(Curve.P_256, "sintetica-es256");
    private static final ECKey FIRMA_INCORRECTA = claveEc(Curve.P_256, FIRMA.getKeyID());
    private static final ECKey FIRMA_ES384 = claveEc(Curve.P_384, "sintetica-es384");
    private static final RSAKey FIRMA_RSA = claveRsa();
    private static final AtomicInteger CONSULTAS_JWKS = new AtomicInteger();
    private static final HttpServer JWKS = iniciarJwks();
    private static final HttpClient CLIENTE = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int puerto;

    @MockitoBean(name = "clienteCuentaActiva")
    private CloseableHttpClient clienteAuth;

    @BeforeEach
    void cuentaExistente() throws Exception {
        responderAuth(200, "{\"id\":\"" + SUBJECT + "\"}");
    }

    private void responderAuth(int estado, String cuerpo) throws Exception {
        when(clienteAuth.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenAnswer(invocacion -> {
                    var respuesta = new BasicClassicHttpResponse(estado);
                    respuesta.setEntity(new StringEntity(cuerpo, ContentType.APPLICATION_JSON));
                    return respuesta;
                });
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EMISOR);
        propiedades.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        propiedades.add("seguridad.cuenta.publishable-key", () -> "sb_publishable_sintetica");
    }

    @AfterAll
    static void cerrarJwks() {
        JWKS.stop(0);
    }

    @Test
    void aceptaEs256ValidoYObtieneUuidDelContexto() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/prueba/privado", token(claims()));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo(SUBJECT);
        assertThat(CONSULTAS_JWKS.get()).isPositive();
        verify(clienteAuth).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
    }

    @Test
    void eliminacionSeRechazaEnLaSiguienteSolicitudSinCachePositiva() throws Exception {
        String jwt = token(claims());
        assertThat(enviar("GET", "/prueba/privado", jwt).statusCode()).isEqualTo(200);
        responderAuth(403, "{\"code\":\"user_not_found\"}");
        comprobar401(enviar("GET", "/prueba/privado", jwt));
        verify(clienteAuth, times(2)).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
        // No queda identidad autenticada reutilizable entre solicitudes.
        comprobar401(enviar("GET", "/prueba/privado", null));
    }

    @Test
    void falloDeAuthNoEjecutaEndpointYDevuelve503Controlado() throws Exception {
        responderAuth(503, "detalle remoto que no debe exponerse");
        var respuesta = enviar("GET", "/prueba/privado", token(claims()));
        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(respuesta.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/problem+json");
        assertThat(respuesta.body()).contains("Verificacion de identidad temporalmente no disponible.")
                .doesNotContain(SUBJECT, "Bearer", "Exception");
    }

    @Test
    void healthConJwtValidoNoConsultaCuentaNiDependeDeAuth() throws Exception {
        responderAuth(503, "detalle remoto que no debe exponerse");
        assertThat(enviar("GET", "/actuator/health", token(claims())).statusCode()).isEqualTo(200);
        verifyNoInteractions(clienteAuth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/usuarios/me", "/api/v1/pedidos", "/api/v1/reportes/pedidos"})
    void cuentaRechazadaNoAlcanzaRutasProtegidas(String ruta) throws Exception {
        responderAuth(401, "{}");
        comprobar401(enviar("GET", ruta, token(claims())));
    }

    @Test
    void idDevueltoDistintoDelSubRechazaCon401() throws Exception {
        responderAuth(200, "{\"id\":\"22222222-2222-2222-2222-222222222222\"}");
        comprobar401(enviar("GET", "/prueba/privado", token(claims())));
    }

    @Test
    void falloDeConexionDevuelve503SinDetallesInternos() throws Exception {
        when(clienteAuth.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenThrow(new IOException("detalle interno que no debe exponerse"));
        var respuesta = enviar("GET", "/prueba/privado", token(claims()));
        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(respuesta.body()).doesNotContain("detalle interno", "Exception", SUBJECT);
    }

    @Test
    void aceptaNbfPasadoYAudienciaDentroDeUnaLista() throws Exception {
        JWTClaimsSet.Builder claims = claims()
                .notBeforeTime(Date.from(Instant.now().minusSeconds(10)))
                .audience(List.of("otra-audiencia", "authenticated"));

        assertThat(enviar("GET", "/prueba/privado", token(claims)).statusCode()).isEqualTo(200);
    }

    @Test
    void healthEsPublico() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/actuator/health", null);

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"status\":\"UP\"");
        verifyNoInteractions(clienteAuth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/prueba/privado", "/pedidos", "/usuarios", "/actuator/info",
            "/actuator/health/otro", "/login", "/logout"})
    void protegePorDefectoLasDemasRutas(String ruta) throws Exception {
        comprobar401(enviar("GET", ruta, null));
    }

    @Test
    void rechazaFirmaDeOtraClaveAunqueCoincidaKid() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(FIRMA.getKeyID()).build(), claims().build());
        jwt.sign(new ECDSASigner(FIRMA_INCORRECTA));

        comprobar401(enviar("GET", "/prueba/privado", jwt.serialize()));
    }

    @Test
    void rechazaPayloadAlteradoSinVolverAFirmar() throws Exception {
        String[] partes = token(claims()).split("\\.");
        String alterado = partes[0] + "." + Base64URL.encode(claims()
                .subject("22222222-2222-2222-2222-222222222222").build().toString())
                + "." + partes[2];

        comprobar401(enviar("GET", "/prueba/privado", alterado));
    }

    @Test
    void rechazaKidDesconocido() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID("clave-no-publicada").build(), claims().build());
        jwt.sign(new ECDSASigner(FIRMA));

        comprobar401(enviar("GET", "/prueba/privado", jwt.serialize()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "HS256", "RS256", "ES384"})
    void rechazaTodoAlgoritmoDistintoDeEs256(String algoritmo) throws Exception {
        String jwt;
        if ("none".equals(algoritmo)) {
            jwt = new PlainJWT(claims().build()).serialize();
        } else {
            SignedJWT firmado = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.parse(algoritmo))
                    .keyID("RS256".equals(algoritmo) ? FIRMA_RSA.getKeyID() : FIRMA_ES384.getKeyID())
                    .build(), claims().build());
            switch (algoritmo) {
                case "RS256" -> firmado.sign(new RSASSASigner(FIRMA_RSA));
                case "ES384" -> firmado.sign(new ECDSASigner(FIRMA_ES384));
                default -> {
                    byte[] secretoSintetico = new byte[32];
                    new SecureRandom().nextBytes(secretoSintetico);
                    firmado.sign(new MACSigner(secretoSintetico));
                }
            }
            jwt = firmado.serialize();
        }

        comprobar401(enviar("GET", "/prueba/privado", jwt));
    }

    static Stream<Arguments> claimsInvalidos() {
        return Stream.of(
                invalido("emisor ajeno", b -> b.issuer("https://otro.invalid/auth/v1")),
                invalido("emisor ausente", b -> b.issuer(null)),
                invalido("audiencia incorrecta", b -> b.audience("anon")),
                invalido("audiencia ausente", b -> b.claim("aud", null)),
                invalido("expirado", b -> b.expirationTime(Date.from(Instant.now().minusSeconds(5)))),
                invalido("exp ausente", b -> b.expirationTime(null)),
                invalido("nbf futuro", b -> b.notBeforeTime(Date.from(Instant.now().plusSeconds(120)))),
                invalido("sub ausente", b -> b.subject(null)),
                invalido("sub vacio", b -> b.subject("")),
                invalido("sub no UUID", b -> b.subject("no-es-un-uuid")),
                invalido("sub UUID abreviado", b -> b.subject("1-1-1-1-1")),
                invalido("rol ausente", b -> b.claim("role", null)),
                invalido("rol service_role", b -> b.claim("role", "service_role")),
                invalido("rol supabase_admin", b -> b.claim("role", "supabase_admin")),
                invalido("rol anon", b -> b.claim("role", "anon")),
                invalido("rol con tipo incorrecto", b -> b.claim("role", List.of("authenticated"))));
    }

    @ParameterizedTest(name = "rechaza {0}")
    @MethodSource("claimsInvalidos")
    void rechazaClaimsInvalidos(String escenario, Consumer<JWTClaimsSet.Builder> modificar) throws Exception {
        JWTClaimsSet.Builder claims = claims();
        modificar.accept(claims);

        comprobar401(enviar("GET", "/prueba/privado", token(claims)));
        verifyNoInteractions(clienteAuth);
    }

    @Test
    void rechazaTokenMalformado() throws Exception {
        comprobar401(enviar("GET", "/prueba/privado", "no-es-un-jwt"));
    }

    @Test
    void responde403SiFaltaLaAutoridadRequerida() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/prueba/restringido", token(claims()));

        assertThat(respuesta.statusCode()).isEqualTo(403);
    }

    @Test
    void permiteElAccesoConLaAutoridadRequerida() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/prueba/restringido",
                token(claims().claim("scope", "prueba:restringida")));

        assertThat(respuesta.statusCode()).isEqualTo(200);
    }

    @Test
    void postBearerNoNecesitaCsrfNiCreaSesionYNoConservaIdentidad() throws Exception {
        HttpResponse<String> respuesta = enviar("POST", "/prueba/privado", token(claims()));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.headers().allValues("Set-Cookie")).isEmpty();
        comprobar401(enviar("GET", "/prueba/privado", null));
    }

    @Test
    void noHabilitaCorsIndiscriminado() throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(uri("/actuator/health"))
                .header("Origin", "https://otro.invalid").GET().build();

        HttpResponse<String> respuesta = CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    private static Arguments invalido(String nombre, Consumer<JWTClaimsSet.Builder> modificar) {
        return arguments(nombre, modificar);
    }

    private static JWTClaimsSet.Builder claims() {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder().issuer(EMISOR).audience("authenticated")
                .subject(SUBJECT).claim("role", "authenticated")
                .issueTime(Date.from(ahora.minusSeconds(30)))
                .expirationTime(Date.from(ahora.plusSeconds(300)));
    }

    private static String token(JWTClaimsSet.Builder claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(FIRMA.getKeyID()).build(), claims.build());
        jwt.sign(new ECDSASigner(FIRMA));
        return jwt.serialize();
    }

    private URI uri(String ruta) {
        return URI.create("http://127.0.0.1:" + puerto + ruta);
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String token) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(uri(ruta)).timeout(Duration.ofSeconds(10));
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
        return CLIENTE.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void comprobar401(HttpResponse<String> respuesta) {
        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.headers().firstValue("WWW-Authenticate").orElse("")).startsWith("Bearer");
        assertThat(respuesta.headers().firstValue("Location")).isEmpty();
    }

    private static ECKey claveEc(Curve curva, String kid) {
        try {
            return new ECKeyGenerator(curva).keyID(kid).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo generar una clave sintetica.", ex);
        }
    }

    private static RSAKey claveRsa() {
        try {
            return new RSAKeyGenerator(2048).keyID("sintetica-rs256").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo generar una clave sintetica.", ex);
        }
    }

    private static HttpServer iniciarJwks() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] contenido = new JWKSet(List.of(FIRMA.toPublicJWK(), FIRMA_ES384.toPublicJWK(),
                    FIRMA_RSA.toPublicJWK())).toString().getBytes(StandardCharsets.UTF_8);
            servidor.createContext("/jwks", intercambio -> {
                CONSULTAS_JWKS.incrementAndGet();
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, contenido.length);
                try (var salida = intercambio.getResponseBody()) {
                    salida.write(contenido);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo iniciar el JWKS local.", ex);
        }
    }

    /** Endpoints solo de prueba; no se agregan rutas de negocio al backend. */
    @RestController
    static class EndpointsPrueba {

        private final UsuarioAutenticado usuario;

        EndpointsPrueba(UsuarioAutenticado usuario) {
            this.usuario = usuario;
        }

        @GetMapping("/prueba/privado")
        String privado() {
            return usuario.usuarioActual().toString();
        }

        @PostMapping("/prueba/privado")
        String crear() {
            return usuario.usuarioActual().toString();
        }

        @GetMapping("/prueba/restringido")
        @PreAuthorize("hasAuthority('SCOPE_prueba:restringida')")
        String restringido() {
            return "permitido";
        }
    }
}
