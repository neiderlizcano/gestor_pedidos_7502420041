package com.neider.gestorpedidos.configuracion.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.VerificarCuentaActiva;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.http.VerificarCuentaActivaHttp;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CuentaActivaHttpConfiguracionTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(CuentaActivaHttpConfiguracion.class)
            .withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.invalid/auth/v1");

    @Test
    void preparaAdaptadorSinAbrirConexiones() {
        contexto.withPropertyValues("seguridad.cuenta.publishable-key=sb_publishable_sintetica")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(VerificarCuentaActiva.class);
                    assertThat(ctx.getBean(VerificarCuentaActiva.class)).isInstanceOf(VerificarCuentaActivaHttp.class);
                });
    }

    @Test
    void conservaConfiguracionPublishableExistente() {
        contexto.withPropertyValues("cuenta.publishable-key=sb_publishable_sintetica")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void sinClaveNoArrancaEnModoInseguro() {
        contexto.withPropertyValues("seguridad.cuenta.publishable-key=")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
