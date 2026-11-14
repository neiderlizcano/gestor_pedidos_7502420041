package com.neider.gestorpedidos.pedidos.infraestructura.entrada.rest;

import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.OTRO_USUARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.PROPIETARIO;
import static com.neider.gestorpedidos.pedidos.aplicacion.soporte.PedidosDePrueba.pedido;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.neider.gestorpedidos.configuracion.pedidos.PedidosConfiguracion;
import com.neider.gestorpedidos.configuracion.seguridad.SeguridadConfiguracion;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
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

/** MockMvc con filtros, decoder ES256 y casos de uso reales; solo el repositorio es simulado. */
@WebMvcTest(PedidosControlador.class)
@ActiveProfiles("persistencia")
@Import({SeguridadConfiguracion.class, UsuarioAutenticado.class, PedidosConfiguracion.class,
        PedidosErroresHandler.class, PedidosControladorTest.InfraestructuraSeguridad.class})
class PedidosControladorTest {

    @MockitoBean
    private VerificarCuentaActiva cuentas;

    private static final String RUTA = "/api/v1/pedidos";
    private static final String EMISOR = "https://supabase.invalid/auth/v1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ECKey FIRMA = clave();
    private static final HttpServer JWKS = iniciarJwks();
    private static final String SOLICITUD = """
            {
              "fecha": "2026-01-10",
              "fechaEnvio": "2026-01-11",
              "fechaEntrega": "2026-01-12",
              "cliente": "Cliente",
              "proveedor": "Proveedor",
              "valor": 100.50,
              "estado": "Pendiente",
              "pais": "Colombia",
              "departamento": "Santander",
              "ciudad": "Bucaramanga",
              "nomenclaturaVivienda": "Calle 1 #2-3",
              "propina": 2.00
            }
            """;

    // El slice MVC de Boot 4 no importa la infraestructura web de seguridad por defecto.
    // Se habilita, pero la unica cadena es la configuracion real de produccion importada arriba.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class InfraestructuraSeguridad {
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RepositorioPedidos repositorio;

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
    void creaCon201LocationYJsonSinPropietario() throws Exception {
        simularCreacion();

        mvc.perform(autenticada("POST", RUTA).content(SOLICITUD))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, RUTA + "/7"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"id\":7," + SOLICITUD.trim().substring(1), JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.usuarioId").doesNotExist())
                .andExpect(jsonPath("$.usuario_id").doesNotExist());

        var captor = ArgumentCaptor.forClass(Pedido.class);
        verify(repositorio).crear(captor.capture());
        assertThat(captor.getValue().usuarioId()).isEqualTo(PROPIETARIO);
        assertThat(captor.getValue().id()).isNull();
    }

    @Test
    void consultaUnPedidoPropio() throws Exception {
        when(repositorio.buscarPorIdYPropietario(7L, PROPIETARIO))
                .thenReturn(Optional.of(pedido(7L, PROPIETARIO)));

        mvc.perform(autenticada("GET", RUTA + "/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.fechaEnvio").value("2026-01-11"))
                .andExpect(jsonPath("$.valor").value(100.50))
                .andExpect(jsonPath("$.usuarioId").doesNotExist());

        verify(repositorio).buscarPorIdYPropietario(7L, PROPIETARIO);
    }

    @Test
    void listaPedidosPropiosComoArregloJson() throws Exception {
        when(repositorio.listarPorPropietario(PROPIETARIO)).thenReturn(List.of(pedido(7L, PROPIETARIO)));

        mvc.perform(autenticada("GET", RUTA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].usuarioId").doesNotExist());

        verify(repositorio).listarPorPropietario(PROPIETARIO);
    }

    @Test
    void listaVaciaEs200YNo404() throws Exception {
        when(repositorio.listarPorPropietario(PROPIETARIO)).thenReturn(List.of());

        mvc.perform(autenticada("GET", RUTA)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void putReemplazaDatosEditablesYConservaIdYPropietario() throws Exception {
        simularActualizacion();

        mvc.perform(autenticada("PUT", RUTA + "/7").content(conCampo("cliente", "\"Cliente actualizado\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.cliente").value("Cliente actualizado"))
                .andExpect(jsonPath("$.usuarioId").doesNotExist());

        var captor = ArgumentCaptor.forClass(Pedido.class);
        verify(repositorio).actualizarPorIdYPropietario(eq(7L), eq(PROPIETARIO), captor.capture());
        assertThat(captor.getValue().id()).isEqualTo(7L);
        assertThat(captor.getValue().usuarioId()).isEqualTo(PROPIETARIO);
    }

    @Test
    void putNoAceptaUnaActualizacionParcial() throws Exception {
        mvc.perform(autenticada("PUT", RUTA + "/7").content("{\"cliente\":\"Cambio\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(repositorio);
    }

    @Test
    void eliminaCon204SinCuerpo() throws Exception {
        when(repositorio.eliminarPorIdYPropietario(7L, PROPIETARIO)).thenReturn(true);

        mvc.perform(autenticada("DELETE", RUTA + "/7"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        verify(repositorio).eliminarPorIdYPropietario(7L, PROPIETARIO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fecha", "fechaEnvio", "fechaEntrega", "cliente", "proveedor", "valor",
            "estado", "pais", "departamento", "ciudad", "nomenclaturaVivienda", "propina"})
    void rechazaCamposObligatoriosAusentes(String campo) throws Exception {
        ObjectNode cuerpo = (ObjectNode) JSON.readTree(SOLICITUD);
        cuerpo.remove(campo);
        comprobar400(cuerpo.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fecha", "fechaEnvio", "fechaEntrega", "cliente", "proveedor", "valor",
            "estado", "pais", "departamento", "ciudad", "nomenclaturaVivienda", "propina"})
    void rechazaCamposObligatoriosNulos(String campo) throws Exception {
        comprobar400(conCampo(campo, "null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor", "estado", "pais", "departamento", "ciudad",
            "nomenclaturaVivienda"})
    void rechazaTextosEnBlanco(String campo) throws Exception {
        comprobar400(conCampo(campo, "\"  \""));
    }

    @ParameterizedTest
    @CsvSource({"valor,0", "valor,-1", "propina,-0.01", "valor,10000000000", "propina,10000000000",
            "valor,1.001", "propina,1.001"})
    void rechazaImportesInvalidosSegunDominio(String campo, String literal) throws Exception {
        comprobar400(conCampo(campo, literal));
    }

    @ParameterizedTest
    @CsvSource({"fechaEnvio,2026-01-09", "fechaEntrega,2026-01-10"})
    void rechazaFechasFueraDeOrden(String campo, String fecha) throws Exception {
        comprobar400(conCampo(campo, "\"" + fecha + "\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-30", "10/01/2026", "no-fecha"})
    void rechazaFechasMalformadas(String fecha) throws Exception {
        comprobar400(conCampo("fecha", "\"" + fecha + "\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "[]", "null"})
    void rechazaCuerposJsonInvalidos(String cuerpo) throws Exception {
        comprobar400(cuerpo);
    }

    @Test
    void permiteCerosDecimalesAdicionalesSinInventarRestriccionesNiEstados() throws Exception {
        simularCreacion();
        ObjectNode cuerpo = (ObjectNode) JSON.readTree(conCampo("valor", "100.5000"));
        cuerpo.put("estado", "Estado de texto libre");
        cuerpo.put("propina", 0);

        mvc.perform(autenticada("POST", RUTA).content(cuerpo.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(100.50))
                .andExpect(jsonPath("$.propina").value(0))
                .andExpect(jsonPath("$.estado").value("Estado de texto libre"));
    }

    @ParameterizedTest
    @CsvSource({"POST,usuario_id", "POST,usuarioId", "POST,id", "PUT,usuario_id", "PUT,usuarioId", "PUT,id"})
    void ignoraIdentificadoresNoConfiablesDelCuerpo(String metodo, String campo) throws Exception {
        if ("POST".equals(metodo)) {
            simularCreacion();
        } else {
            simularActualizacion();
        }
        String cuerpo = conCampo(campo, "id".equals(campo) ? "999" : "\"" + OTRO_USUARIO + "\"");

        mvc.perform(autenticada(metodo, RUTA + ("PUT".equals(metodo) ? "/7" : ""))
                        .header("X-Usuario-Id", OTRO_USUARIO.toString()).content(cuerpo))
                .andExpect(status().is("POST".equals(metodo) ? 201 : 200))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(content().string(not(containsString(OTRO_USUARIO.toString()))));

        var captor = ArgumentCaptor.forClass(Pedido.class);
        if ("POST".equals(metodo)) {
            verify(repositorio).crear(captor.capture());
            assertThat(captor.getValue().id()).isNull();
        } else {
            verify(repositorio).actualizarPorIdYPropietario(eq(7L), eq(PROPIETARIO), captor.capture());
            assertThat(captor.getValue().id()).isEqualTo(7L);
        }
        assertThat(captor.getValue().usuarioId()).isEqualTo(PROPIETARIO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"usuario_id", "usuarioId"})
    void listadoIgnoraPropietariosEnParametros(String campo) throws Exception {
        when(repositorio.listarPorPropietario(PROPIETARIO)).thenReturn(List.of(pedido(7L, PROPIETARIO)));

        mvc.perform(autenticada("GET", RUTA).param(campo, OTRO_USUARIO.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(7));

        verify(repositorio).listarPorPropietario(PROPIETARIO);
    }

    @ParameterizedTest
    @CsvSource({"GET,999", "GET,9", "PUT,999", "PUT,9", "DELETE,999", "DELETE,9"})
    void inexistentesYAjenoTienenElMismo404SinDatosDelPropietario(String metodo, Long id) throws Exception {
        if (id == 9L && !"DELETE".equals(metodo)) {
            // El pedido solo seria accesible con otra identidad; el caso de uso nunca la solicita.
            when(repositorio.buscarPorIdYPropietario(9L, OTRO_USUARIO))
                    .thenReturn(Optional.of(pedido(9L, OTRO_USUARIO)));
        }

        mvc.perform(autenticada(metodo, RUTA + "/" + id).content(SOLICITUD))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Pedido no encontrado."))
                .andExpect(content().string(not(containsString(OTRO_USUARIO.toString()))));

        if ("DELETE".equals(metodo)) {
            verify(repositorio).eliminarPorIdYPropietario(id, PROPIETARIO);
        } else {
            verify(repositorio).buscarPorIdYPropietario(id, PROPIETARIO);
        }
    }

    static Stream<Arguments> identificadoresInvalidos() {
        return Stream.of("GET", "PUT", "DELETE").flatMap(metodo ->
                Stream.of("0", "-1", "abc", "9223372036854775808").map(id -> Arguments.of(metodo, id)));
    }

    @ParameterizedTest
    @MethodSource("identificadoresInvalidos")
    void rechazaIdentificadoresInvalidos(String metodo, String id) throws Exception {
        mvc.perform(autenticada(metodo, RUTA + "/" + id).content(SOLICITUD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(repositorio);
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/v1/pedidos", "GET,/api/v1/pedidos", "GET,/api/v1/pedidos/7",
            "PUT,/api/v1/pedidos/7", "DELETE,/api/v1/pedidos/7"})
    void todosLosEndpointsRequierenAutenticacion(String metodo, String ruta) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(metodo), ruta)
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        verifyNoInteractions(repositorio);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformado", "expirado", "firma", "HS256"})
    void decoderRealRechazaTokensInvalidos(String caso) throws Exception {
        String bearer;
        if ("malformado".equals(caso)) {
            bearer = "no-es-un-jwt";
        } else if ("HS256".equals(caso)) {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(PROPIETARIO,
                    Instant.now().plusSeconds(300)));
            jwt.sign(new MACSigner(new byte[32])); // Secreto sintetico, solo en memoria.
            bearer = jwt.serialize();
        } else {
            bearer = token("firma".equals(caso) ? clave() : FIRMA, PROPIETARIO,
                    "expirado".equals(caso) ? Instant.now().minusSeconds(30) : Instant.now().plusSeconds(300));
        }

        mvc.perform(request(HttpMethod.GET, RUTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
        verifyNoInteractions(repositorio);
    }

    @Test
    void otraIdentidadJwtSeleccionaSusPropiosPedidosSinConservarLaAnterior() throws Exception {
        when(repositorio.listarPorPropietario(PROPIETARIO)).thenReturn(List.of(pedido(7L, PROPIETARIO)));
        when(repositorio.listarPorPropietario(OTRO_USUARIO)).thenReturn(List.of(pedido(9L, OTRO_USUARIO)));

        mvc.perform(autenticada("GET", RUTA)).andExpect(jsonPath("$[0].id").value(7));
        mvc.perform(request(HttpMethod.GET, RUTA).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(FIRMA, OTRO_USUARIO, Instant.now().plusSeconds(300))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(9));
        mvc.perform(request(HttpMethod.GET, RUTA)).andExpect(status().isUnauthorized());

        verify(repositorio).listarPorPropietario(PROPIETARIO);
        verify(repositorio).listarPorPropietario(OTRO_USUARIO);
    }

    private void comprobar400(String cuerpo) throws Exception {
        mvc.perform(autenticada("POST", RUTA).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Solicitud de pedido invalida."));
        verifyNoInteractions(repositorio);
    }

    private void simularCreacion() {
        when(repositorio.crear(any(Pedido.class))).thenAnswer(invocacion ->
                ((Pedido) invocacion.getArgument(0)).asignarId(7L));
    }

    private void simularActualizacion() {
        when(repositorio.buscarPorIdYPropietario(7L, PROPIETARIO))
                .thenReturn(Optional.of(pedido(7L, PROPIETARIO)));
        when(repositorio.actualizarPorIdYPropietario(eq(7L), eq(PROPIETARIO), any(Pedido.class)))
                .thenAnswer(invocacion -> Optional.of(invocacion.getArgument(2, Pedido.class)));
    }

    private static String conCampo(String campo, String literalJson) {
        ObjectNode cuerpo = (ObjectNode) JSON.readTree(SOLICITUD);
        cuerpo.set(campo, JSON.readTree(literalJson));
        return cuerpo.toString();
    }

    private MockHttpServletRequestBuilder autenticada(String metodo, String ruta) throws JOSEException {
        return request(HttpMethod.valueOf(metodo), ruta).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + token(FIRMA, PROPIETARIO, Instant.now().plusSeconds(300)));
    }

    private static JWTClaimsSet claims(UUID sujeto, Instant expiracion) {
        return new JWTClaimsSet.Builder().issuer(EMISOR).audience("authenticated")
                .subject(sujeto.toString()).claim("role", "authenticated")
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(expiracion)).build();
    }

    private static String token(ECKey clave, UUID sujeto, Instant expiracion) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .keyID(FIRMA.getKeyID()).build(), claims(sujeto, expiracion));
        jwt.sign(new ECDSASigner(clave));
        return jwt.serialize();
    }

    private static ECKey clave() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("pruebas-pedidos-es256").generate();
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
