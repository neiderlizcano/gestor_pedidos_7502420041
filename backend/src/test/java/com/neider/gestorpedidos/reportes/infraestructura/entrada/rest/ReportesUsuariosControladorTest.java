package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_EMAIL;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.OTRO_ID;
import static com.neider.gestorpedidos.usuarios.aplicacion.soporte.UsuariosDePrueba.perfil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.UUID;
import java.util.stream.Stream;

import com.neider.gestorpedidos.configuracion.reportes.ReportesConfiguracion;
import com.neider.gestorpedidos.configuracion.reportes.ReportesUsuariosConfiguracion;
import com.neider.gestorpedidos.configuracion.seguridad.SeguridadConfiguracion;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CampoPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CoincidenciaPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportesUsuarios;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

/** Casos, identidad y filtros reales; consultas simuladas, firmas efimeras y JWKS local. */
@WebMvcTest(ReportesUsuariosControlador.class)
@ActiveProfiles("persistencia")
@Import({SeguridadConfiguracion.class, UsuarioAutenticado.class, ReportesConfiguracion.class,
        ReportesUsuariosConfiguracion.class, ReportesUsuariosErroresHandler.class,
        ReportesUsuariosControladorTest.InfraestructuraSeguridad.class})
class ReportesUsuariosControladorTest {

    private static final String RUTA = "/api/v1/reportes/usuarios/";
    private static final String EMISOR = "https://supabase.invalid/auth/v1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ECKey FIRMA = clave();
    private static final HttpServer JWKS = iniciarJwks();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultasReportesUsuarios consultas;

    @MockitoBean
    private ConsultasReportes consultasPedidos;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class InfraestructuraSeguridad {
        // No define otra cadena: habilita la infraestructura para la cadena real importada.
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

    @AfterEach
    void noUsaLosReportesDePedidos() {
        verifyNoInteractions(consultasPedidos);
    }

    @ParameterizedTest
    @CsvSource({"nombre,exacta,Neider Lizcano", "nombre,prefijo,Neider", "username,exacta,nlizcano", "username,prefijo,nli"})
    void buscaNombreYUsernameConCoincidenciaExactaOPrefijo(String campo, String coincidencia, String termino) throws Exception {
        when(consultas.porNombreUsername(eq(ID), any())).thenReturn(List.of(perfil()));

        mvc.perform(nombre(campo, termino, coincidencia))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(JSON.writeValueAsString(List.of(perfil())), JsonCompareMode.STRICT));

        verify(consultas).porNombreUsername(ID, new FiltroNombreUsername(CampoPerfil.desde(campo), termino,
                CoincidenciaPerfil.desde(coincidencia)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "Ana%_!\\", "x' OR 1=1 --"})
    void textoConComodinesOInyeccionSeTrataComoFiltroLiteral(String termino) throws Exception {
        mvc.perform(nombre("nombre", termino, "prefijo")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(consultas).porNombreUsername(ID, new FiltroNombreUsername(CampoPerfil.NOMBRE, termino, CoincidenciaPerfil.PREFIJO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"neider@example.com", "otro@example.com", "no-existe@example.com", "NEIDER@example.com"})
    void comparaElCorreoAlmacenadoExactamenteYNoRevelaCoincidenciasAjenas(String correo) throws Exception {
        when(consultas.porCorreo(eq(ID), any())).thenAnswer(invocacion -> {
            FiltroCorreo filtro = invocacion.getArgument(1);
            return EMAIL.equals(filtro.correo()) ? List.of(perfil()) : List.of();
        });

        mvc.perform(autenticada("correo").param("correo", correo))
                .andExpect(status().isOk())
                .andExpect(content().json(JSON.writeValueAsString(EMAIL.equals(correo) ? List.of(perfil()) : List.of()),
                        JsonCompareMode.STRICT));
        verify(consultas).porCorreo(ID, new FiltroCorreo(correo));
    }

    @Test
    void comparaContraPersistenciaNoContraElCorreoDelJwt() throws Exception {
        String filtro = "solo-en-jwt@example.com";
        String bearer = token(claims(ID).claim("email", filtro), FIRMA);

        mvc.perform(get(RUTA + "correo").param("correo", filtro).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(consultas).porCorreo(ID, new FiltroCorreo(filtro));
    }

    @ParameterizedTest
    @ValueSource(strings = {"campo", "termino", "coincidencia"})
    void todosLosParametrosDeNombreSonObligatorios(String ausente) throws Exception {
        var solicitud = autenticada("nombre-username");
        if (!"campo".equals(ausente)) solicitud.param("campo", "nombre");
        if (!"termino".equals(ausente)) solicitud.param("termino", "Persona");
        if (!"coincidencia".equals(ausente)) solicitud.param("coincidencia", "exacta");
        comprobar400(solicitud);
    }

    @Test
    void correoEsObligatorio() throws Exception {
        comprobar400(autenticada("correo"));
    }

    static Stream<Arguments> filtrosInvalidos() {
        return Stream.of(
                Arguments.of("campo", ""), Arguments.of("campo", " "), Arguments.of("campo", "email"),
                Arguments.of("campo", "id"), Arguments.of("campo", "NOMBRE"),
                Arguments.of("campo", "nombre; DROP TABLE usuarios"),
                Arguments.of("coincidencia", ""), Arguments.of("coincidencia", " "),
                Arguments.of("coincidencia", "contiene"), Arguments.of("coincidencia", "EXACTA"),
                Arguments.of("coincidencia", "exacta OR 1=1"),
                Arguments.of("termino", ""), Arguments.of("termino", "  "), Arguments.of("termino", "x".repeat(201)));
    }

    @ParameterizedTest
    @MethodSource("filtrosInvalidos")
    void rechazaFiltrosDeNombreInvalidos(String parametro, String valor) throws Exception {
        comprobar400(nombre("campo".equals(parametro) ? valor : "nombre",
                "termino".equals(parametro) ? valor : "Persona", "coincidencia".equals(parametro) ? valor : "exacta"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "sin-arroba", "persona@", "@example.com", "x' OR 1=1 --@example.com"})
    void rechazaCorreosVaciosOConFormatoInvalido(String correo) throws Exception {
        comprobar400(autenticada("correo").param("correo", correo));
    }

    @Test
    void aplicaLimitesDeLongitudSinTruncarLosParametros() throws Exception {
        String correo254 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(53) + ".invalid";
        assertThat(correo254).hasSize(254);
        mvc.perform(nombre("username", "x".repeat(200), "exacta")).andExpect(status().isOk());
        mvc.perform(autenticada("correo").param("correo", correo254)).andExpect(status().isOk());
        verify(consultas).porCorreo(ID, new FiltroCorreo(correo254));
        // El exceso se valida en una peticion separada antes de llegar a los casos de uso.
        mvc.perform(autenticada("correo").param("correo", "a" + correo254))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    static Stream<Arguments> parametrosNoPermitidos() {
        return Stream.of("nombre-username", "correo").flatMap(reporte -> Stream.of("id", "usuarioId", "usuario_id", "admin")
                .map(campo -> Arguments.of(reporte, campo)));
    }

    @ParameterizedTest
    @MethodSource("parametrosNoPermitidos")
    void noAceptaUuidDelPropietarioNiFiltrosGlobales(String reporte, String parametro) throws Exception {
        var solicitud = "correo".equals(reporte) ? autenticada(reporte).param("correo", EMAIL) : nombre("nombre", "Persona", "exacta");
        comprobar400(solicitud.param(parametro, OTRO_ID.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre-username", "correo"})
    void rechazaParametrosRepetidos(String reporte) throws Exception {
        var solicitud = "correo".equals(reporte) ? autenticada(reporte).param("correo", EMAIL, OTRO_EMAIL)
                : nombre("nombre", "Persona", "exacta").param("campo", "username");
        comprobar400(solicitud);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre-username", "correo"})
    void ausenciaDeCoincidenciaSiempreEsUnaColeccionVacia(String reporte) throws Exception {
        mvc.perform("correo".equals(reporte) ? autenticada(reporte).param("correo", OTRO_EMAIL)
                        : nombre("nombre", "Nombre ajeno", "exacta"))
                .andExpect(status().isOk()).andExpect(content().json("[]", JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"nombre-username", "correo"})
    void sinJwtSeResponde401AntesDeProcesarFiltros(String reporte) throws Exception {
        mvc.perform(get(RUTA + reporte)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
        verifyNoInteractions(consultas);
    }

    @Test
    void mantieneLaValidacionCriptograficaDelJwt() throws Exception {
        mvc.perform(get(RUTA + "correo").param("correo", EMAIL).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(claims(ID), clave())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(consultas);
    }

    @Test
    void identidadEsLaDelJwtAunqueElClienteEnvieOtraCabeceraYSinSesionPosterior() throws Exception {
        when(consultas.porCorreo(eq(ID), any())).thenReturn(List.of(perfil()));
        when(consultas.porCorreo(eq(OTRO_ID), any())).thenReturn(List.of(perfil(OTRO_ID, "otro", "Otro", OTRO_EMAIL)));

        mvc.perform(autenticada("correo").param("correo", EMAIL).header("X-Usuario-Id", OTRO_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ID.toString()));
        mvc.perform(get(RUTA + "correo").param("correo", OTRO_EMAIL).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(claims(OTRO_ID), FIRMA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(OTRO_ID.toString()));
        mvc.perform(get(RUTA + "correo").param("correo", EMAIL)).andExpect(status().isUnauthorized());
        verify(consultas).porCorreo(ID, new FiltroCorreo(EMAIL));
        verify(consultas).porCorreo(OTRO_ID, new FiltroCorreo(OTRO_EMAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "11111111-1111-1111-1111-111111111111"})
    void noExistenListadosGlobalesNiRutasPorUuid(String ruta) throws Exception {
        mvc.perform(autenticada(ruta)).andExpect(status().isNotFound());
        verifyNoInteractions(consultas);
    }

    private void comprobar400(MockHttpServletRequestBuilder solicitud) throws Exception {
        mvc.perform(solicitud).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Solicitud de reporte invalida."));
        verifyNoInteractions(consultas);
    }

    private MockHttpServletRequestBuilder nombre(String campo, String termino, String coincidencia) throws JOSEException {
        return autenticada("nombre-username").param("campo", campo).param("termino", termino).param("coincidencia", coincidencia);
    }

    private MockHttpServletRequestBuilder autenticada(String reporte) throws JOSEException {
        // Estos reportes solo necesitan sub validado, no el correo del JWT.
        return get(RUTA + reporte).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(claims(ID), FIRMA));
    }

    private static JWTClaimsSet.Builder claims(UUID sujeto) {
        return new JWTClaimsSet.Builder().issuer(EMISOR).audience("authenticated").subject(sujeto.toString())
                .claim("role", "authenticated").issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String token(JWTClaimsSet.Builder claims, ECKey firma) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(FIRMA.getKeyID()).build(), claims.build());
        jwt.sign(new ECDSASigner(firma));
        return jwt.serialize();
    }

    private static ECKey clave() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("pruebas-reportes-usuarios-es256").generate();
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
