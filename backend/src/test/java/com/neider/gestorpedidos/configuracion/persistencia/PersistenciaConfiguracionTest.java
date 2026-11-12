package com.neider.gestorpedidos.configuracion.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.neider.gestorpedidos.pedidos.infraestructura.salida.jdbc.RepositorioPedidosJdbc;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc.RepositorioUsuariosJdbc;
import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** Pruebas de configuracion sin abrir conexiones ni usar el entorno real. */
class PersistenciaConfiguracionTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(context -> {
                var fuentes = context.getEnvironment().getPropertySources();
                fuentes.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                fuentes.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                new ConfigDataApplicationContextInitializer().initialize(context);
            })
            .withPropertyValues(
                    "SUPABASE_AUTH_ISSUER=https://supabase.invalid/auth/v1",
                    "SUPABASE_JWKS_URI=https://supabase.invalid/auth/v1/.well-known/jwks.json")
            .withUserConfiguration(AplicacionPrueba.class);

    @Test
    void sinPerfilArrancaSinConfiguracionNiInfraestructuraJdbc() {
        contexto.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class)
                    .doesNotHaveBean(JdbcTemplate.class)
                    .doesNotHaveBean(RepositorioPedidosJdbc.class)
                    .doesNotHaveBean(RepositorioUsuariosJdbc.class)
                    .doesNotHaveBean(EjecutorRlsJdbc.class)
                    .doesNotHaveBean(JdbcTransactionManager.class);
            assertThat(context.getEnvironment().getProperty("DB_URL")).isNull();
            assertThat(context.getEnvironment().getProperty("DB_USERNAME")).isNull();
            assertThat(context.getEnvironment().getProperty("DB_PASSWORD")).isNull();
            assertThat(context.getEnvironment().getProperty("DB_SSL_ROOT_CERT")).isNull();
            assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("0.0.0.0");
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("8080");
        });
    }

    @Test
    void puertoDelEntornoSobrescribeElValorLocalSinActivarJdbc() {
        contexto.withInitializer(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("render-sintetico", Map.of("PORT", "10000"))))
                .run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class);
                    assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("10000");
                    assertThat(context.getEnvironment().getProperty("server.address")).isEqualTo("0.0.0.0");
                });
    }

    @Test
    void sinPerfilLasVariablesDeConexionNoActivanJdbc() {
        contexto.withInitializer(context -> context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("conexion-sintetica", configuracionSintetica())))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class)
                        .doesNotHaveBean(JdbcTemplate.class)
                        .doesNotHaveBean(RepositorioPedidosJdbc.class)
                        .doesNotHaveBean(RepositorioUsuariosJdbc.class));
    }

    @Test
    void perfilPreparaHikariYJdbcTemplateSinAbrirElPool() {
        conPerfil(configuracionSintetica()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(HikariDataSource.class)
                    .hasSingleBean(JdbcTemplate.class)
                    .hasSingleBean(RepositorioPedidosJdbc.class)
                    .hasSingleBean(RepositorioUsuariosJdbc.class)
                    .hasSingleBean(EjecutorRlsJdbc.class)
                    .hasSingleBean(JdbcTransactionManager.class);
            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertThat(dataSource.isRunning()).isFalse();
            assertThat(dataSource.getJdbcUrl()).isEqualTo(configuracionSintetica().get("DB_URL"));
            assertThat(dataSource.getUsername()).isEqualTo(configuracionSintetica().get("DB_USERNAME"));
            assertThat(dataSource.getPassword()).isEqualTo(configuracionSintetica().get("DB_PASSWORD"));
            assertThat(context.getBean(JdbcTemplate.class).getDataSource()).isSameAs(dataSource);
            assertThat(context.getBean(JdbcTransactionManager.class).getDataSource()).isSameAs(dataSource);
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(3);
            assertThat(dataSource.getMinimumIdle()).isZero();
            assertThat(dataSource.getConnectionTimeout()).isEqualTo(10000);
            assertThat(dataSource.getValidationTimeout()).isEqualTo(5000);
            assertThat(dataSource.getIdleTimeout()).isEqualTo(600000);
            assertThat(dataSource.getMaxLifetime()).isEqualTo(1800000);
            assertThat(dataSource.getDataSourceProperties()).containsEntry("sslmode", "verify-full");
            assertThat(dataSource.getDataSourceProperties()).containsEntry("sslrootcert",
                    configuracionSintetica().get("DB_SSL_ROOT_CERT"));
            assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(context.getEnvironment().getProperty(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                    .isEqualTo("https://supabase.invalid/auth/v1");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"})
    void perfilRechazaVariablesAusentes(String variable) {
        Map<String, Object> propiedades = configuracionSintetica();
        propiedades.remove(variable);

        conPerfil(propiedades).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"})
    void perfilRechazaVariablesVacias(String variable) {
        Map<String, Object> propiedades = configuracionSintetica();
        propiedades.put(variable, "");

        conPerfil(propiedades).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"})
    void perfilRechazaVariablesConSoloEspacios(String variable) {
        Map<String, Object> propiedades = configuracionSintetica();
        propiedades.put(variable, "   ");

        conPerfil(propiedades).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void perfilRechazaUrlQueNoSeaJdbcPostgresql() {
        Map<String, Object> propiedades = configuracionSintetica();
        propiedades.put("DB_URL", "no-es-una-url-jdbc");

        conPerfil(propiedades).run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner conPerfil(Map<String, Object> propiedades) {
        return contexto.withPropertyValues("spring.profiles.active=persistencia")
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("conexion-sintetica", propiedades)));
    }

    private Map<String, Object> configuracionSintetica() {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put("DB_URL", "jdbc:postgresql://db.invalid:5432/pruebas");
        propiedades.put("DB_USERNAME", "app_pruebas");
        propiedades.put("DB_PASSWORD", "solo-pruebas");
        // Solo se comprueba el binding: esta ruta no existe ni se lee ningun certificado.
        propiedades.put("DB_SSL_ROOT_CERT", "/ruta-sintetica/supabase-ca.crt");
        return propiedades;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({PersistenciaConfiguracion.class, EjecutorRlsJdbc.class, UsuarioAutenticado.class})
    @ComponentScan(basePackageClasses = {RepositorioPedidosJdbc.class, RepositorioUsuariosJdbc.class})
    static class AplicacionPrueba {
    }
}
