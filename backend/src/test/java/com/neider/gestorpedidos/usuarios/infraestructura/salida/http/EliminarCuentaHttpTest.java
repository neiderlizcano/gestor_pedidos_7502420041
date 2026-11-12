package com.neider.gestorpedidos.usuarios.infraestructura.salida.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.UUID;

import com.neider.gestorpedidos.usuarios.aplicacion.error.EliminacionCuentaException;
import com.neider.gestorpedidos.usuarios.aplicacion.error.EliminacionCuentaException.Motivo;

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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Transporte simulado y JWT sintetico: ninguna conexion HTTP/Supabase. */
class EliminarCuentaHttpTest {
    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String URL = "https://project.invalid/functions/v1/delete-my-account";
    private final CloseableHttpClient cliente = mock(CloseableHttpClient.class);
    private final EliminarCuentaHttp adaptador = new EliminarCuentaHttp(cliente, URL, "");

    @BeforeEach
    void autenticar() {
        var jwt = Jwt.withTokenValue("jwt-sintetico-local").header("alg", "ES256").subject(A.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enviaSoloJwtOriginalEnDeleteSinEntidadYEspera204() throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenAnswer(invocacion -> {
                    ClassicHttpRequest req = invocacion.getArgument(1);
                    assertThat(req.getMethod()).isEqualTo("DELETE");
                    assertThat(req.getUri().toString()).isEqualTo(URL);
                    assertThat(req.getEntity()).isNull();
                    assertThat(req.getFirstHeader("Authorization").getValue()).isEqualTo("Bearer jwt-sintetico-local");
                    assertThat(req.getFirstHeader("apikey")).isNull();
                    assertThat(req.getFirstHeader("Content-Type")).isNull();
                    return new BasicClassicHttpResponse(204);
                });
        adaptador.eliminar(A);
        verify(cliente).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
        verifyNoMoreInteractions(cliente);
    }

    @Test
    void admiteSoloPublishableOpcional() throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenAnswer(invocacion -> {
                    ClassicHttpRequest req = invocacion.getArgument(1);
                    assertThat(req.getFirstHeader("apikey").getValue()).isEqualTo("sb_publishable_prueba");
                    return new BasicClassicHttpResponse(204);
                });
        new EliminarCuentaHttp(cliente, URL, "sb_publishable_prueba").eliminar(A);
    }

    @Test
    void sinJwtNoInvocaHttp() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> adaptador.eliminar(A)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(cliente);
    }

    @Test
    void identidadAjenaNoInvocaHttp() {
        assertThatThrownBy(() -> adaptador.eliminar(B)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @CsvSource({"200,SERVICIO_REMOTO", "202,SERVICIO_REMOTO", "301,SERVICIO_REMOTO", "302,SERVICIO_REMOTO",
            "307,SERVICIO_REMOTO", "308,SERVICIO_REMOTO", "400,SERVICIO_REMOTO", "401,RECHAZADA",
            "403,SERVICIO_REMOTO", "404,SERVICIO_REMOTO", "409,SERVICIO_REMOTO", "429,NO_DISPONIBLE",
            "500,SERVICIO_REMOTO", "502,SERVICIO_REMOTO", "503,NO_DISPONIBLE", "504,TIEMPO_AGOTADO"})
    void estadoNoConfirmadoNoEsExitoNiSeReintenta(int estado, Motivo motivo) throws Exception {
        var response = new BasicClassicHttpResponse(estado);
        response.setHeader("Location", "https://otro.invalid");
        response.setEntity(new StringEntity("secreto SQL jwt-sintetico-local", ContentType.TEXT_PLAIN));
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class))).thenReturn(response);
        comprobarFallo(motivo);
        verify(cliente).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
        verifyNoMoreInteractions(cliente);
    }

    @Test
    void conflictoSoloConContratoExactoSinDetallesLibres() throws Exception {
        var response = new BasicClassicHttpResponse(409);
        response.setEntity(new StringEntity("{\"code\":\"STORAGE_CONFLICT\"}", ContentType.APPLICATION_JSON));
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class))).thenReturn(response);
        comprobarFallo(Motivo.CONFLICTO_STORAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{", "{\"code\":\"UNKNOWN\"}",
            "{\"code\":\"STORAGE_CONFLICT\",\"detail\":\"SQL\"}",
            "{\"code\":\"STORAGE_CONFLICT\"} {}", "{\"code\":\"UNKNOWN\",\"code\":\"STORAGE_CONFLICT\"}"})
    void noAdivinaConflictoPorErrorDesconocido(String cuerpo) throws Exception {
        var response = new BasicClassicHttpResponse(409);
        response.setEntity(new StringEntity(cuerpo, ContentType.APPLICATION_JSON));
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class))).thenReturn(response);
        comprobarFallo(Motivo.SERVICIO_REMOTO);
    }

    @Test
    void limitaCuerpoDeError() throws Exception {
        var response = new BasicClassicHttpResponse(409);
        response.setEntity(new StringEntity(" ".repeat(1025) + "{\"code\":\"STORAGE_CONFLICT\"}", ContentType.APPLICATION_JSON));
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class))).thenReturn(response);
        comprobarFallo(Motivo.SERVICIO_REMOTO);
    }

    @Test
    void timeoutSinReintentoNiCausaSensible() throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenThrow(new SocketTimeoutException("jwt-sintetico-local"));
        comprobarFallo(Motivo.TIEMPO_AGOTADO);
        verify(cliente).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
        verifyNoMoreInteractions(cliente);
    }

    @Test
    void conexionFallidaSinReintento() throws Exception {
        when(cliente.executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class)))
                .thenThrow(new IOException("jwt-sintetico-local"));
        comprobarFallo(Motivo.NO_DISPONIBLE);
        verify(cliente).executeOpen(isNull(), any(ClassicHttpRequest.class), any(HttpContext.class));
        verifyNoMoreInteractions(cliente);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "http://project.invalid/functions/v1/delete-my-account",
            "https://admin:password@project.invalid/functions/v1/delete-my-account",
            "https://project.invalid/functions/v1/delete-my-account?id=otro",
            "https://project.invalid/functions/v1/delete-my-account#otro",
            "https://project.invalid:8443/functions/v1/delete-my-account",
            "https://project.invalid/otra", "https://project.invalid/functions/v1/%64elete-my-account"})
    void rechazaUrlNoFijaOSinHttpsSinExponerElValor(String url) {
        assertThatThrownBy(() -> new EliminarCuentaHttp(cliente, url, ""))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessage("Se requiere una URL HTTPS fija de delete-my-account.");
        verifyNoInteractions(cliente);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sb_secret_administrativa", "eyJ.jwt.admin", "service_role", "sb_publishable_x\r\nAuthorization: otro"})
    void rechazaClavesAdministrativasYCabecerasInyectadas(String clave) {
        assertThatThrownBy(() -> new EliminarCuentaHttp(cliente, URL, clave))
                .isInstanceOf(IllegalArgumentException.class).hasNoCause()
                .hasMessage("Solo se admite una clave publishable opcional.");
    }

    private void comprobarFallo(Motivo motivo) {
        assertThatThrownBy(() -> adaptador.eliminar(A)).isInstanceOfSatisfying(EliminacionCuentaException.class,
                fallo -> {
                    assertThat(fallo.motivo()).isEqualTo(motivo);
                    assertThat(fallo.getCause()).isNull();
                    assertThat(fallo.getMessage()).doesNotContain("SQL", "jwt-sintetico-local");
                });
    }
}
