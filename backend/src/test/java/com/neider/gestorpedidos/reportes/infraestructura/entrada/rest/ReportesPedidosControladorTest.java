package com.neider.gestorpedidos.reportes.infraestructura.entrada.rest;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.pedido;
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
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import com.neider.gestorpedidos.configuracion.reportes.ReportesConfiguracion;
import com.neider.gestorpedidos.configuracion.seguridad.SeguridadConfiguracion;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FechaBase;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
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
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP con cadena ES256 y casos reales; puerto de consultas simulado y JWKS solo local. */
@WebMvcTest(ReportesPedidosControlador.class)
@ActiveProfiles("persistencia")
@Import({SeguridadConfiguracion.class, UsuarioAutenticado.class, ReportesConfiguracion.class,
        ReportesErroresHandler.class, ReportesPedidosControladorTest.InfraestructuraSeguridad.class})
class ReportesPedidosControladorTest {

    @MockitoBean
    private VerificarCuentaActiva cuentas;

    private static final String RUTA = "/api/v1/reportes/pedidos/";
    private static final String EMISOR = "https://supabase.invalid/auth/v1";
    private static final ECKey FIRMA = clave();
    private static final HttpServer JWKS = iniciarJwks();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultasReportes consultas;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class InfraestructuraSeguridad {
        // Solo infraestructura: se conserva la cadena real, no se permite acceso anonimo.
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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void filtrosDeEstadoYFechasSePuedenUsarSolosOCombinados(int mascara) throws Exception {
        var solicitud = autenticada("estado-fechas");
        if ((mascara & 1) != 0) solicitud.param("estado", "Pendiente");
        if ((mascara & 2) != 0) solicitud.param("desde", "2026-01-01");
        if ((mascara & 4) != 0) solicitud.param("hasta", "2026-01-31");

        mvc.perform(solicitud).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0)).andExpect(jsonPath("$.tamano").value(20));

        var filtro = ArgumentCaptor.forClass(FiltroEstadoFechas.class);
        verify(consultas).porEstadoFechas(eq(PROPIETARIO), filtro.capture(), eq(new PaginacionReporte(0, 20)));
        assertThat(filtro.getValue()).isEqualTo(new FiltroEstadoFechas((mascara & 1) != 0 ? "Pendiente" : null,
                (mascara & 2) != 0 ? LocalDate.of(2026, 1, 1) : null,
                (mascara & 4) != 0 ? LocalDate.of(2026, 1, 31) : null, FechaBase.FECHA));
    }

    @ParameterizedTest
    @CsvSource({"fecha,FECHA", "fecha_envio,FECHA_ENVIO", "fecha_entrega,FECHA_ENTREGA"})
    void seleccionaUnaDeLasFechasRealesYPermiteElMismoDia(String valor, FechaBase esperado) throws Exception {
        mvc.perform(autenticada("estado-fechas").param("fechaBase", valor)
                        .param("desde", "2026-01-10").param("hasta", "2026-01-10"))
                .andExpect(status().isOk());
        var filtro = ArgumentCaptor.forClass(FiltroEstadoFechas.class);
        verify(consultas).porEstadoFechas(eq(PROPIETARIO), filtro.capture(), any());
        assertThat(filtro.getValue().fechaBase()).isEqualTo(esperado);
        assertThat(filtro.getValue().desde()).isEqualTo(filtro.getValue().hasta());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fecha", "fecha_envio", "fecha_entrega"})
    void rechazaRangosInvertidosEnCualquierFechaBase(String base) throws Exception {
        comprobar400(autenticada("estado-fechas").param("fechaBase", base)
                .param("desde", "2026-02-01").param("hasta", "2026-01-31"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void ubicacionesIndividualesYCombinadasLleganAlCasoDeUso(int mascara) throws Exception {
        var solicitud = autenticada("ubicacion");
        if ((mascara & 1) != 0) solicitud.param("pais", "Colombia");
        if ((mascara & 2) != 0) solicitud.param("departamento", "Santander");
        if ((mascara & 4) != 0) solicitud.param("ciudad", "Bucaramanga");

        mvc.perform(solicitud).andExpect(status().isOk());

        var filtro = ArgumentCaptor.forClass(FiltroUbicacion.class);
        verify(consultas).porUbicacion(eq(PROPIETARIO), filtro.capture(), eq(new PaginacionReporte(0, 20)));
        assertThat(filtro.getValue()).isEqualTo(new FiltroUbicacion((mascara & 1) != 0 ? "Colombia" : null,
                (mascara & 2) != 0 ? "Santander" : null, (mascara & 4) != 0 ? "Bucaramanga" : null));
    }

    @Test
    void ubicacionSinFiltrosEs400() throws Exception {
        comprobar400(autenticada("ubicacion"));
    }

    static Stream<Arguments> filtrosInvalidos() {
        return Stream.of(
                Arguments.of("estado-fechas", "estado", ""),
                Arguments.of("estado-fechas", "estado", "  "),
                Arguments.of("estado-fechas", "desde", ""),
                Arguments.of("estado-fechas", "hasta", "  "),
                Arguments.of("estado-fechas", "desde", "2026-02-30"),
                Arguments.of("estado-fechas", "hasta", "31/01/2026"),
                Arguments.of("estado-fechas", "desde", "no-fecha"),
                Arguments.of("estado-fechas", "fechaBase", "fechaEnvio"),
                Arguments.of("estado-fechas", "fechaBase", "fecha; DROP TABLE pedidos"),
                Arguments.of("estado-fechas", "fechaBase", ""),
                Arguments.of("ubicacion", "pais", ""),
                Arguments.of("ubicacion", "departamento", "  "),
                Arguments.of("ubicacion", "ciudad", ""));
    }

    @ParameterizedTest
    @MethodSource("filtrosInvalidos")
    void validaFormatosYTextosPresentes(String reporte, String parametro, String valor) throws Exception {
        comprobar400(autenticada(reporte).param(parametro, valor));
    }

    static Stream<Arguments> paginacionesInvalidas() {
        return Stream.of("estado-fechas", "ubicacion").flatMap(reporte -> Stream.of(
                new String[] {"pagina", "-1"}, new String[] {"pagina", "abc"}, new String[] {"pagina", "2147483648"},
                new String[] {"tamano", "0"}, new String[] {"tamano", "101"}, new String[] {"tamano", "abc"},
                new String[] {"tamano", ""}).map(par -> Arguments.of(reporte, par[0], par[1])));
    }

    @ParameterizedTest
    @MethodSource("paginacionesInvalidas")
    void validaPaginacionEnAmbosReportes(String reporte, String parametro, String valor) throws Exception {
        var solicitud = autenticada(reporte).param(parametro, valor);
        if ("ubicacion".equals(reporte)) solicitud.param("pais", "Colombia");
        comprobar400(solicitud);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado-fechas", "ubicacion"})
    void paginaVaciaEs200ConMetadatos(String reporte) throws Exception {
        var solicitud = autenticada(reporte).param("pagina", "9").param("tamano", "100");
        if ("ubicacion".equals(reporte)) solicitud.param("ciudad", "Ciudad");

        mvc.perform(solicitud).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"contenido\":[],\"pagina\":9,\"tamano\":100,\"haySiguiente\":false}",
                        JsonCompareMode.STRICT));
    }

    @Test
    void respuestaPaginadaTieneCamposRealesSinPropietarioYNoIncluyeLaFilaExtra() throws Exception {
        when(consultas.porEstadoFechas(eq(PROPIETARIO), any(), any()))
                .thenReturn(List.of(pedido(7L, PROPIETARIO), pedido(8L, PROPIETARIO)));

        mvc.perform(autenticada("estado-fechas").param("estado", "Pendiente").param("pagina", "2").param("tamano", "1"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"contenido":[{"id":7,"fecha":"2026-01-10","fechaEnvio":"2026-01-11",
                          "fechaEntrega":"2026-01-12","cliente":"Cliente","proveedor":"Proveedor",
                          "valor":100.50,"estado":"Pendiente","pais":"Colombia","departamento":"Santander",
                          "ciudad":"Bucaramanga","nomenclaturaVivienda":"Calle 1 #2-3","propina":2.00}],
                         "pagina":2,"tamano":1,"haySiguiente":true}
                        """, JsonCompareMode.STRICT));
        verify(consultas).porEstadoFechas(eq(PROPIETARIO), any(), eq(new PaginacionReporte(2, 1)));
    }

    static Stream<Arguments> parametrosNoPermitidos() {
        return Stream.of("estado-fechas", "ubicacion").flatMap(reporte -> Stream.of(
                "usuario_id", "usuarioId", "id", "sort").map(campo -> Arguments.of(reporte, campo)));
    }

    @ParameterizedTest
    @MethodSource("parametrosNoPermitidos")
    void rechazaPropietarioYOtrosParametrosDesconocidos(String reporte, String parametro) throws Exception {
        var solicitud = autenticada(reporte).param(parametro, OTRO_USUARIO.toString());
        if ("ubicacion".equals(reporte)) solicitud.param("pais", "Colombia");
        comprobar400(solicitud);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado-fechas", "ubicacion"})
    void noAceptaParametrosRepetidos(String reporte) throws Exception {
        comprobar400(autenticada(reporte).param("estado-fechas".equals(reporte) ? "estado" : "pais", "uno", "dos"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"estado-fechas", "ubicacion"})
    void sinAutenticacionResponde401AntesDeConsultar(String reporte) throws Exception {
        mvc.perform(get(RUTA + reporte)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
        verifyNoInteractions(consultas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformado", "firma", "rol", "HS256"})
    void decoderRealSigueExigiendoFirmaEs256YRolDeUsuario(String caso) throws Exception {
        String bearer;
        if ("malformado".equals(caso)) {
            bearer = "no-es-jwt";
        } else if ("HS256".equals(caso)) {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(PROPIETARIO.toString()).build());
            jwt.sign(new MACSigner(new byte[32]));
            bearer = jwt.serialize();
        } else {
            bearer = token("rol".equals(caso) ? claims(PROPIETARIO.toString()).claim("role", "service_role")
                    : claims(PROPIETARIO.toString()), "firma".equals(caso) ? clave() : FIRMA);
        }
        mvc.perform(get(RUTA + "estado-fechas").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(consultas);
    }

    @Test
    void identidadSeObtieneDelJwtNuncaDeCabecerasYNoSeConservaEntrePeticiones() throws Exception {
        when(consultas.porUbicacion(eq(PROPIETARIO), any(), any())).thenReturn(List.of(pedido(7L, PROPIETARIO)));
        when(consultas.porUbicacion(eq(OTRO_USUARIO), any(), any())).thenReturn(List.of(pedido(9L, OTRO_USUARIO)));

        mvc.perform(autenticada("ubicacion").param("pais", "Colombia").header("X-Usuario-Id", OTRO_USUARIO.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(7));
        mvc.perform(get(RUTA + "ubicacion").param("pais", "Colombia").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(claims(OTRO_USUARIO.toString()), FIRMA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].id").value(9));
        mvc.perform(get(RUTA + "ubicacion").param("pais", "Colombia")).andExpect(status().isUnauthorized());
        verify(consultas).porUbicacion(eq(PROPIETARIO), any(), any());
        verify(consultas).porUbicacion(eq(OTRO_USUARIO), any(), any());
    }

    private void comprobar400(MockHttpServletRequestBuilder solicitud) throws Exception {
        mvc.perform(solicitud).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Solicitud de reporte invalida."));
        verifyNoInteractions(consultas);
    }

    private MockHttpServletRequestBuilder autenticada(String reporte) throws JOSEException {
        // No requiere email: el puerto de reportes solo consume el UUID validado.
        return get(RUTA + reporte).header(HttpHeaders.AUTHORIZATION, "Bearer " + token(claims(PROPIETARIO.toString()), FIRMA));
    }

    private static JWTClaimsSet.Builder claims(String sujeto) {
        return new JWTClaimsSet.Builder().issuer(EMISOR).audience("authenticated").subject(sujeto)
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
            return new ECKeyGenerator(Curve.P_256).keyID("pruebas-reportes-es256").generate();
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
