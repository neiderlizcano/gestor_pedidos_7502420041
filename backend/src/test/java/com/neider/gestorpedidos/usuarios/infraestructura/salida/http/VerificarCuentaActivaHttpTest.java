package com.neider.gestorpedidos.usuarios.infraestructura.salida.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.CuentaNoAutorizadaException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.VerificacionCuentaNoDisponibleException;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Transporte simulado, valores sinteticos efimeros; nunca conecta a servicios externos. */
class VerificarCuentaActivaHttpTest {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMISOR = "https://project.invalid/auth/v1";
    private final CloseableHttpClient cliente = mock(CloseableHttpClient.class);
    private final String credencialSintetica = UUID.randomUUID().toString();
    private final String claveSintetica = "sb_publishable_" + UUID.randomUUID().toString().replace("-", "");
    private final VerificarCuentaActivaHttp adaptador = new VerificarCuentaActivaHttp(cliente, EMISOR, claveSintetica);

    @BeforeEach
    void autenticar() {
        var jwt = Jwt.withTokenValue(credencialSintetica).header("alg", "ES256").subject(ID.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultaUsuarioOriginalSinPerfilSinCacheYLimpiaCabeceras() throws Exception {
        ClassicHttpRequest[] enviada = new ClassicHttpRequest[1];
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenAnswer(invocacion -> {
                    ClassicHttpRequest req = invocacion.getArgument(1);
                    enviada[0] = req;
                    assertThat(req.getMethod()).isEqualTo("GET");
                    assertThat(req.getUri().toString()).isEqualTo(EMISOR + "/user");
                    assertThat(req.getEntity()).isNull();
                    // Las aserciones no imprimen valores de credenciales, ni siquiera sinteticas.
                    assertThat(req.getFirstHeader("Authorization").getValue()
                            .equals("Bearer " + credencialSintetica)).isTrue();
                    assertThat(req.getFirstHeader("apikey").getValue().equals(claveSintetica)).isTrue();
                    return respuesta(200, "{\"id\":\"" + ID + "\"}");
                });
        adaptador.verificar(ID);
        assertThat(enviada[0].containsHeader("Authorization")).isFalse();
        assertThat(enviada[0].containsHeader("apikey")).isFalse();
        adaptador.verificar(ID);
        verify(cliente, times(2)).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404})
    void cuentaEliminadaORechazadaNoAutoriza(int estado) throws Exception {
        preparar(estado, "{\"code\":\"user_not_found\"}");
        assertThatThrownBy(() -> adaptador.verificar(ID)).isInstanceOf(CuentaNoAutorizadaException.class)
                .hasMessage("Autenticacion requerida.").hasNoCause();
    }

    @Test
    void identificadorDevueltoNoCoincideDeniegaAcceso() throws Exception {
        preparar(200, "{\"id\":\"22222222-2222-2222-2222-222222222222\"}");
        assertThatThrownBy(() -> adaptador.verificar(ID)).isInstanceOf(CuentaNoAutorizadaException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {204, 301, 302, 307, 308, 400, 404, 429, 500, 502, 503, 504})
    void servicioNoConfirmadoNoAutorizaNiSeReintenta(int estado) throws Exception {
        preparar(estado, "respuesta remota que no debe publicarse");
        comprobarNoDisponible();
        verify(cliente).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "[]", "{\"id\":123}",
            "{\"id\":\"a\",\"id\":\"b\"}", "{} {}"})
    void respuestaInvalidaNoAutoriza(String cuerpo) throws Exception {
        preparar(200, cuerpo);
        comprobarNoDisponible();
    }

    @Test
    void limitaTamanoDelCuerpo() throws Exception {
        preparar(200, " ".repeat(65537));
        comprobarNoDisponible();
    }

    @Test
    void rechazaContenidoNoJson() throws Exception {
        var respuesta = respuesta(200, "{}");
        respuesta.setEntity(new StringEntity("{}", ContentType.TEXT_HTML));
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenReturn(respuesta);
        comprobarNoDisponible();
    }

    @Test
    void conexionFallidaNoExponeCausa() throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenThrow(new IOException("detalle interno simulado"));
        comprobarNoDisponible();
    }

    @Test
    void timeoutNoExponeCausaNiSeReintenta() throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenThrow(new SocketTimeoutException("detalle interno simulado"));
        comprobarNoDisponible();
        verify(cliente).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
    }

    @Test
    void identidadLocalInconsistenteNoEnviaCredenciales() {
        assertThatThrownBy(() -> adaptador.verificar(UUID.randomUUID()))
                .isInstanceOf(CuentaNoAutorizadaException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> adaptador.verificar(ID)).isInstanceOf(CuentaNoAutorizadaException.class);
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://project.invalid/auth/v1", "https://project.invalid/auth/v1?otro=1",
            "https://project.invalid/auth/v1#otro", "https://project.invalid:8443/auth/v1",
            "https://usuario@project.invalid/auth/v1", "https://project.invalid/auth/%761",
            "https://project.invalid/otra", ""})
    void emisorInseguroNoSeAceptaNiSeExpone(String emisor) {
        assertThatThrownBy(() -> new VerificarCuentaActivaHttp(cliente, emisor, claveSintetica))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessage("Se requiere un emisor HTTPS fijo de Supabase Auth.");
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "sb_secret_no_admitida", "service_role", "sb_publishable_x\r\nHeader: valor"})
    void exigePublishableSinClavesAdministrativas(String clave) {
        assertThatThrownBy(() -> new VerificarCuentaActivaHttp(cliente, EMISOR, clave))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessage("Se requiere una clave publishable de Supabase Auth.");
    }

    private void preparar(int estado, String cuerpo) throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenReturn(respuesta(estado, cuerpo));
    }

    private BasicClassicHttpResponse respuesta(int estado, String cuerpo) {
        var respuesta = new BasicClassicHttpResponse(estado);
        respuesta.setEntity(new StringEntity(cuerpo, ContentType.APPLICATION_JSON));
        return respuesta;
    }

    private void comprobarNoDisponible() {
        assertThatThrownBy(() -> adaptador.verificar(ID))
                .isInstanceOf(VerificacionCuentaNoDisponibleException.class).hasNoCause()
                .hasMessage("Verificacion de identidad temporalmente no disponible.");
    }
}
