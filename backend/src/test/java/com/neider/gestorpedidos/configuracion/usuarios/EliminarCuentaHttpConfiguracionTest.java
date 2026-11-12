package com.neider.gestorpedidos.configuracion.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.http.EliminarCuentaHttp;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc.EliminarCuentaJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Repository;

class EliminarCuentaHttpConfiguracionTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(EliminarCuentaHttpConfiguracion.class)
            .withPropertyValues("spring.profiles.active=persistencia");

    @Test
    void soloUnAdaptadorActivoSinAbrirConexiones() {
        contexto.withPropertyValues("cuenta.edge-function-url=https://project.invalid/functions/v1/delete-my-account")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().hasSingleBean(EliminarCuenta.class);
                    assertThat(ctx.getBean(EliminarCuenta.class)).isInstanceOf(EliminarCuentaHttp.class);
                    assertThat(EliminarCuentaJdbc.class.getAnnotation(Repository.class)).isNull();
                });
    }

    @Test
    void urlInseguraImpideActivarElAdaptador() {
        contexto.withPropertyValues("cuenta.edge-function-url=http://project.invalid/functions/v1/delete-my-account")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
