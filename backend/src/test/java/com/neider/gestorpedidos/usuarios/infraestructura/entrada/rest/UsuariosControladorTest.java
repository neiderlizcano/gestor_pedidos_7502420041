package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.perfil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.neider.gestorpedidos.configuracion.seguridad.SeguridadConfiguracion;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.configuracion.usuarios.UsuariosConfiguracion;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Casos y filtros reales, repositorio simulado, JWT ES256 efimero y JWKS solo local. */
@WebMvcTest(UsuariosControlador.class)
@ActiveProfiles("persistencia")
@Import({SeguridadConfiguracion.class, UsuarioAutenticado.class, UsuariosConfiguracion.class,
        UsuariosErroresHandler.class, UsuariosControladorTest.InfraestructuraSeguridad.class})
class UsuariosControladorTest {

    private static final String RUTA = "/api/v1/usuarios";
    private static final String EMISOR = "https://supabase.invalid/auth/v1";
    private static final String SOLICITUD = "{\"username\":\"nlizcano\",\"nombre\":\"Neider Lizcano\"}";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ECKey FIRMA = clave();
    private static final HttpServer JWKS = iniciarJwks();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepositorioUsuarios repositorio;

    // Habilita infraestructura, no una cadena permisiva: se importa la cadena real de produccion.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class InfraestructuraSeguridad {
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EMISOR);
        propiedades.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void cerrarJwks() {
        JWKS.stop(0);
    }

    @Test
    void creaPerfilConUuidYCorreoDelJwtYLocationPropia() throws Exception {
        when(repositorio.crear(any(Usuario.class)))
                .thenAnswer(invocacion -> Optional.of(invocacion.getArgument(0, Usuario.class)));

        mvc.perform(autenticada("POST").content(SOLICITUD))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, RUTA + "/me"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(JSON.writeValueAsString(perfil()), JsonCompareMode.STRICT));

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).crear(captor.capture());
        assertThat(captor.getValue()).isEqualTo(perfil());
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    void consultaSoloPerfilPropioSinConfiarEnCabecerasPersonalizadas() throws Exception {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(perfil()));

        mvc.perform(autenticada("GET").header("X-Usuario-Id", OTRO_ID.toString()).header("X-Email", OTRO_EMAIL))
                .andExpect(status().isOk())
                .andExpect(content().json(JSON.writeValueAsString(perfil()), JsonCompareMode.STRICT));

        verify(repositorio).buscarPorId(ID);
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    void actualizaSoloUsernameYNombreConservandoUuidYCorreoPersistidos() throws Exception {
        Usuario existente = perfil(ID, "anterior", "Nombre anterior", "persistido@example.com");
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(existente));
        when(repositorio.actualizar(any(Usuario.class)))
                .thenAnswer(invocacion -> Optional.of(invocacion.getArgument(0, Usuario.class)));

        mvc.perform(autenticada("PUT").content(SOLICITUD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.email").value("persistido@example.com"))
                .andExpect(jsonPath("$.username").value("nlizcano"))
                .andExpect(jsonPath("$.nombre").value("Neider Lizcano"));

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).buscarPorId(ID);
        verify(repositorio).actualizar(captor.capture());
        assertThat(captor.getValue()).isEqualTo(existente.actualizarPerfil("nlizcano", "Neider Lizcano"));
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    void duplicadosSon409GenericoSinRevelarUuidOCorreoEnConflicto() throws Exception {
        when(repositorio.crear(any(Usuario.class))).thenReturn(Optional.empty());

        mvc.perform(autenticada("POST").content(SOLICITUD))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("No se pudo crear el perfil."))
                .andExpect(content().string(not(containsString(EMAIL))))
                .andExpect(content().string(not(containsString(ID.toString()))));
        verify(repositorio).crear(any(Usuario.class));
        verifyNoMoreInteractions(repositorio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void perfilPropioInexistenteEs404SinConsultarPerfilesAjenos(String metodo) throws Exception {
        when(repositorio.buscarPorId(OTRO_ID)).thenReturn(Optional.of(perfil(OTRO_ID, "otro", "Otro", OTRO_EMAIL)));

        mvc.perform(autenticada(metodo).content(SOLICITUD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Perfil no encontrado."))
                .andExpect(content().string(not(containsString(OTRO_EMAIL))))
                .andExpect(content().string(not(containsString(OTRO_ID.toString()))));
        verify(repositorio).buscarPorId(ID);
        verifyNoMoreInteractions(repositorio);
    }

    static Stream<Arguments> datosInvalidos() {
        return Stream.of("POST", "PUT").flatMap(metodo -> Stream.of(
                "{}", "{\"username\":\"valido\"}", "{\"nombre\":\"Valido\"}",
                "{\"username\":null,\"nombre\":\"Valido\"}", "{\"username\":\"valido\",\"nombre\":null}",
                "{\"username\":\"  \",\"nombre\":\"Valido\"}", "{\"username\":\"valido\",\"nombre\":\"\"}",
                "", "{", "null", "[]").map(cuerpo -> Arguments.of(metodo, cuerpo)));
    }

    @ParameterizedTest
    @MethodSource("datosInvalidos")
    void validaCamposObligatoriosYJson(String metodo, String cuerpo) throws Exception {
        comprobar400(autenticada(metodo).content(cuerpo));
    }

    static Stream<Arguments> camposNoEditables() {
        return Stream.of("POST", "PUT").flatMap(metodo -> Stream.of(
                "id", "usuarioId", "usuario_id", "email", "sub", "role", "user_metadata", "otroCampo")
                .map(campo -> Arguments.of(metodo, campo)));
    }

    @ParameterizedTest
    @MethodSource("camposNoEditables")
    void rechazaIdentidadYCamposNoEditablesDelCliente(String metodo, String campo) throws Exception {
        ObjectNode cuerpo = (ObjectNode) JSON.readTree(SOLICITUD);
        cuerpo.put(campo, "email".equals(campo) ? OTRO_EMAIL : OTRO_ID.toString());
        comprobar400(autenticada(metodo).content(cuerpo.toString()));
    }

    @ParameterizedTest
    @CsvSource({"POST,id", "POST,email", "PUT,id", "PUT,email"})
    void camposNoEditablesTambienSeRechazanSiSonNulos(String metodo, String campo) throws Exception {
        ObjectNode cuerpo = (ObjectNode) JSON.readTree(SOLICITUD);
        cuerpo.putNull(campo);
        comprobar400(autenticada(metodo).content(cuerpo.toString()));
    }

    static Stream<Arguments> parametrosNoAdmitidos() {
        return Stream.of("POST", "GET", "PUT").flatMap(metodo -> Stream.of("id", "usuario_id", "email")
                .map(campo -> Arguments.of(metodo, campo)));
    }

    @ParameterizedTest
    @MethodSource("parametrosNoAdmitidos")
    void rechazaIdentidadEnParametrosDeConsulta(String metodo, String campo) throws Exception {
        comprobar400(autenticada(metodo).param(campo, "email".equals(campo) ? OTRO_EMAIL : OTRO_ID.toString())
                .content(SOLICITUD));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "PUT"})
    void sinJwtResponde401SinInvocarCasosDeUso(String metodo) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), ruta(metodo))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        verifyNoInteractions(repositorio);
    }

    static Stream<Arguments> correosNoConfiables() {
        return Stream.of("POST", "GET", "PUT").flatMap(metodo -> Stream.of(
                "ausente", "vacio", "espacios", "numero", "lista", "metadatos")
                .map(caso -> Arguments.of(metodo, caso)));
    }

    @ParameterizedTest
    @MethodSource("correosNoConfiables")
    void exigeCorreoSuperiorAutenticadoSinRecurrirAMetadatosOCabeceras(String metodo, String caso) throws Exception {
        JWTClaimsSet.Builder datos = claims(ID).claim("user_metadata", Map.of("email", EMAIL));
        switch (caso) {
            case "vacio" -> datos.claim("email", "");
            case "espacios" -> datos.claim("email", " ");
            case "numero" -> datos.claim("email", 123);
            case "lista" -> datos.claim("email", List.of(EMAIL));
            default -> { } // Tanto ausente como metadatos carecen de un email superior confiable.
        }

        mvc.perform(request(HttpMethod.valueOf(metodo), ruta(metodo))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(datos))
                        .header("X-Email", EMAIL).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(repositorio);
    }

    @Test
    void crearIgnoraCorreoEditableEnMetadatosYUsaSoloClaimSuperiorFirmado() throws Exception {
        when(repositorio.crear(any(Usuario.class)))
                .thenAnswer(invocacion -> Optional.of(invocacion.getArgument(0, Usuario.class)));
        String bearer = token(claims(ID).claim("email", EMAIL)
                .claim("user_metadata", Map.of("email", OTRO_EMAIL, "sub", OTRO_ID.toString())));

        mvc.perform(request(HttpMethod.POST, RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(EMAIL));

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).crear(captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(ID);
        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
    }

    @Test
    void rechazaCorreoAlteradoSinVolverAFirmarElJwt() throws Exception {
        String[] partes = token(claims(ID).claim("email", EMAIL)).split("\\.");
        String alterado = partes[0] + "." + Base64URL.encode(claims(ID).claim("email", OTRO_EMAIL).build().toString())
                + "." + partes[2];

        mvc.perform(request(HttpMethod.POST, RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + alterado)
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repositorio);
    }

    @Test
    void identidadesDistintasSoloRecibenSuPerfilYSinJwtPosteriorNoHaySesion() throws Exception {
        when(repositorio.buscarPorId(ID)).thenReturn(Optional.of(perfil()));
        when(repositorio.buscarPorId(OTRO_ID)).thenReturn(Optional.of(perfil(OTRO_ID, "otro", "Otro", OTRO_EMAIL)));

        mvc.perform(autenticada("GET")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ID.toString()));
        mvc.perform(request(HttpMethod.GET, RUTA + "/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(claims(OTRO_ID).claim("email", OTRO_EMAIL))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(OTRO_ID.toString()))
                .andExpect(content().string(not(containsString(EMAIL))));
        mvc.perform(request(HttpMethod.GET, RUTA + "/me")).andExpect(status().isUnauthorized());
        verify(repositorio).buscarPorId(ID);
        verify(repositorio).buscarPorId(OTRO_ID);
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    void noExponeListadoGlobal() throws Exception {
        mvc.perform(request(HttpMethod.GET, RUTA).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(claims(ID).claim("email", EMAIL))))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(repositorio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void noExponeEndpointsQuePermitanElegirUuidAjeno(String metodo) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), RUTA + "/" + OTRO_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(claims(ID).claim("email", EMAIL)))
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isNotFound());
        verifyNoInteractions(repositorio);
    }

    private void comprobar400(MockHttpServletRequestBuilder solicitud) throws Exception {
        mvc.perform(solicitud).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Solicitud de perfil invalida."));
        verifyNoInteractions(repositorio);
    }

    private MockHttpServletRequestBuilder autenticada(String metodo) throws JOSEException {
        return request(HttpMethod.valueOf(metodo), ruta(metodo)).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(claims(ID).claim("email", EMAIL)));
    }

    private static String ruta(String metodo) {
        return RUTA + ("POST".equals(metodo) ? "" : "/me");
    }

    private static JWTClaimsSet.Builder claims(UUID id) {
        return new JWTClaimsSet.Builder().issuer(EMISOR).audience("authenticated").subject(id.toString())
                .claim("role", "authenticated").issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String token(JWTClaimsSet.Builder claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(FIRMA.getKeyID()).build(),
                claims.build());
        jwt.sign(new ECDSASigner(FIRMA));
        return jwt.serialize();
    }

    private static ECKey clave() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("pruebas-usuarios-es256").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo generar la clave efimera de pruebas.", ex);
        }
    }

    private static HttpServer iniciarJwks() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] contenido = new JWKSet(FIRMA.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            servidor.createContext("/jwks", intercambio -> {
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, contenido.length);
                try (var salida = intercambio.getResponseBody()) {
                    salida.write(contenido);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo iniciar el JWKS local de pruebas.", ex);
        }
    }
}
