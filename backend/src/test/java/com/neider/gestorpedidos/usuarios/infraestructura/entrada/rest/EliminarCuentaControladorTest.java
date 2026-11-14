package com.neider.gestorpedidos.usuarios.infraestructura.entrada.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.seguridad.SeguridadConfiguracion;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.configuracion.usuarios.EliminarCuentaConfiguracion;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;
import com.neider.gestorpedidos.usuarios.aplicacion.error.EliminacionCuentaException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.TransactionSystemException;

/** Cadena ES256 real, JWKS efimero local, caso/configuracion reales y salida simulada. */
@WebMvcTest(EliminarCuentaControlador.class)
@ActiveProfiles("persistencia")
@Import({SeguridadConfiguracion.class, UsuarioAutenticado.class, EliminarCuentaConfiguracion.class,
        EliminarCuentaErroresHandler.class, EliminarCuentaControladorTest.Seguridad.class})
class EliminarCuentaControladorTest {

    @MockitoBean
    private VerificarCuentaActiva verificacionCuenta;
    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String RUTA = "/api/v1/usuarios/me";
    private static final String EMISOR = "https://supabase.invalid/auth/v1";
    private static final ECKey FIRMA = clave();
    private static final HttpServer JWKS = iniciarJwks();
    @Autowired private MockMvc mvc;
    @MockitoBean private EliminarCuenta cuentas;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class Seguridad { }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> EMISOR);
        propiedades.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void cerrar() { JWKS.stop(0); }

    @Test
    void devuelve204SinCuerpoUsandoSoloSubSinEmailNiPerfil() throws Exception {
        mvc.perform(autenticada().header("X-Usuario-Id", B).header("X-Email", "b@example.invalid"))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Set-Cookie"));
        verify(cuentas).eliminar(A);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void identidadBSeleccionaSoloB() throws Exception {
        mvc.perform(delete(RUTA).header("Authorization", "Bearer " + token(claims(B), FIRMA)))
                .andExpect(status().isNoContent());
        verify(cuentas).eliminar(B);
        verifyNoMoreInteractions(cuentas);
    }

    @Test
    void jwtAusenteNoLlegaAlCasoDeUso() throws Exception {
        mvc.perform(delete(RUTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(cuentas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformado", "firma", "expirado", "emisor", "sub", "rol", "audiencia"})
    void jwtInvalidoNoLlegaAlPuerto(String tipo) throws Exception {
        var datos = claims(A);
        if (tipo.equals("expirado")) datos.expirationTime(Date.from(Instant.now().minusSeconds(60)));
        if (tipo.equals("emisor")) datos.issuer("https://otro.invalid");
        if (tipo.equals("sub")) datos.subject("no-uuid");
        if (tipo.equals("rol")) datos.claim("role", "service_role");
        if (tipo.equals("audiencia")) datos.audience("otro");
        String bearer = tipo.equals("malformado") ? "no.jwt.valido"
                : token(datos, tipo.equals("firma") ? clave() : FIRMA);
        mvc.perform(delete(RUTA).header("Authorization", "Bearer " + bearer))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cuentas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "usuario_id", "email", "sub", "cualquiera"})
    void rechazaParametrosInclusoVacios(String nombre) throws Exception {
        mvc.perform(autenticada().queryParam(nombre, ""))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", " ", "{\"id\":\"22222222-2222-2222-2222-222222222222\"}", "id=otro"})
    void rechazaCualquierCuerpoSinElegirIdentidad(String cuerpo) throws Exception {
        mvc.perform(autenticada().contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @Test
    void rechazaCuerpoSinContentLengthDeclarado() throws Exception {
        mvc.perform(autenticada().content("{}").with(solicitud -> {
            solicitud.removeHeader("Content-Length");
            solicitud.addHeader("Transfer-Encoding", "chunked");
            return solicitud;
        })).andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @Test
    void rechazaParametrosFormulario() throws Exception {
        mvc.perform(autenticada().contentType(MediaType.APPLICATION_FORM_URLENCODED).param("id", B.toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @Test
    void rechazaFormularioSinParametrosAunqueElFiltroConsumaElCuerpo() throws Exception {
        mvc.perform(autenticada().contentType(MediaType.APPLICATION_FORM_URLENCODED).content("&&"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @Test
    void rechazaMetadataFormularioAunqueYaNoHayaBytesNiLongitud() throws Exception {
        mvc.perform(autenticada().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cuentas);
    }

    @Test
    void uuidEnRutaNoExponeEliminacionAjena() throws Exception {
        mvc.perform(delete("/api/v1/usuarios/" + B).header("Authorization", "Bearer " + token(claims(A), FIRMA)))
                .andExpect(status().isNotFound());
        verifyNoInteractions(cuentas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sql", "commit", "contexto"})
    void erroresNoSon204NiExponenCausas(String tipo) throws Exception {
        RuntimeException fallo = switch (tipo) {
            case "sql" -> new DataAccessResourceFailureException("SELECT secreto auth.users");
            case "commit" -> new TransactionSystemException("SELECT secreto auth.users");
            default -> new IllegalStateException("SELECT secreto auth.users");
        };
        doThrow(fallo).when(cuentas).eliminar(A);
        mvc.perform(autenticada()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("No se pudo completar la eliminacion de la cuenta."))
                .andExpect(content().string(not(containsString("secreto"))))
                .andExpect(content().string(not(containsString("auth.users"))));
    }

    private MockHttpServletRequestBuilder autenticada() throws JOSEException {
        return delete(RUTA).header("Authorization", "Bearer " + token(claims(A), FIRMA));
    }

    @ParameterizedTest
    @CsvSource({"RECHAZADA,401", "CONFLICTO_STORAGE,409", "SERVICIO_REMOTO,502",
            "NO_DISPONIBLE,503", "TIEMPO_AGOTADO,504"})
    void erroresRemotosTienenEstadoSeguroSinFalso204(EliminacionCuentaException.Motivo motivo, int estado) throws Exception {
        doThrow(new EliminacionCuentaException(motivo)).when(cuentas).eliminar(A);
        mvc.perform(autenticada()).andExpect(status().is(estado))
                .andExpect(content().string(not(containsString(A.toString()))));
        verify(cuentas).eliminar(A);
        verifyNoMoreInteractions(cuentas);
    }

    private static JWTClaimsSet.Builder claims(UUID id) {
        return new JWTClaimsSet.Builder().issuer(EMISOR).audience("authenticated").subject(id.toString())
                .claim("role", "authenticated").issueTime(Date.from(Instant.now().minusSeconds(60)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
    }

    private static String token(JWTClaimsSet.Builder datos, ECKey firma) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(FIRMA.getKeyID()).build(), datos.build());
        jwt.sign(new ECDSASigner(firma));
        return jwt.serialize();
    }

    private static ECKey clave() {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID("delete-local-es256").generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("No se pudo generar clave efimera", ex);
        }
    }

    private static HttpServer iniciarJwks() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] contenido = new JWKSet(FIRMA.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            servidor.createContext("/jwks", intercambio -> {
                intercambio.getResponseHeaders().set("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, contenido.length);
                try (var salida = intercambio.getResponseBody()) { salida.write(contenido); }
            });
            servidor.start();
            return servidor;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo iniciar JWKS local", ex);
        }
    }
}
