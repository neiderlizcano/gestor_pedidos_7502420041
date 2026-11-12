package com.neider.gestorpedidos.configuracion.persistencia;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.infraestructura.salida.jdbc.RepositorioPedidosJdbc;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc.RepositorioUsuariosJdbc;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.opentest4j.TestAbortedException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Soporte compartido sin tests ni inicializadores que lean configuracion o abran conexiones. */
abstract class SoporteJdbcRlsIT {

    private static final String ROL_JDBC = "gestor_pedidos_jdbc";
    private static final String BASE_LIMPIA = """
            SELECT current_role = 'gestor_pedidos_jdbc' AND session_user = 'gestor_pedidos_jdbc'
                AND current_setting('role') = 'none'
                AND coalesce(current_setting('request.jwt.claim.sub', true), '') = ''
                AND coalesce(current_setting('request.jwt.claims', true), '') = ''
                AND coalesce(current_setting('request.jwt.claim.role', true), '') = ''
                AND current_setting('transaction_read_only') = 'on'
                AND current_setting('row_security') = 'on'
                AND (SELECT NOT (rolsuper OR rolbypassrls OR rolinherit OR rolcreaterole
                        OR rolcreatedb OR rolreplication)
                     FROM pg_catalog.pg_roles WHERE rolname = session_user)
            """;
    private static final String CONTEXTO_CORRECTO = """
            SELECT current_role = 'authenticated' AND auth.uid() = CAST(:id AS uuid)
                AND current_setting('request.jwt.claim.sub') = CAST(:id AS text)
                AND current_setting('request.jwt.claims')::jsonb
                    = jsonb_build_object('sub', CAST(:id AS text), 'role', 'authenticated')
                AND current_setting('transaction_read_only') = 'on'
                AND current_setting('row_security') = 'on'
                AND row_security_active('public.usuarios'::regclass)
                AND row_security_active('public.pedidos'::regclass)
            """;

    protected HikariDataSource pool;
    protected NamedParameterJdbcTemplate jdbc;
    protected EjecutorRlsJdbc ejecutor;
    protected RepositorioPedidosJdbc pedidos;
    protected RepositorioUsuariosJdbc usuarios;

    @BeforeAll
    void prepararSoloConAutorizacionExplicita() {
        if (!"true".equals(System.getenv("RUN_DB_INTEGRATION"))) {
            throw new TestAbortedException("Integracion JDBC desactivada.");
        }
        // Tambien protege ejecuciones desde IDE, antes de leer cualquier configuracion JDBC.
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext logs) {
            logs.reset();
            logs.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        }
        java.util.logging.Logger.getLogger("org.postgresql").setLevel(java.util.logging.Level.OFF);
        protegido(() -> {
            String url = variableObligatoria("DB_URL");
            URI destino = URI.create(url.startsWith("jdbc:") ? url.substring(5) : "");
            exigir(url.startsWith("jdbc:postgresql://") && destino.getHost() != null
                    && destino.getUserInfo() == null && destino.getRawQuery() == null
                    && destino.getRawFragment() == null && destino.getPath() != null
                    && destino.getPath().length() > 1,
                    "DB_URL debe ser JDBC PostgreSQL sin credenciales ni parametros de URL.");
            String username = variableObligatoria("DB_USERNAME");
            exigir(usuarioConexionPermitido(username),
                    "DB_USERNAME debe ser el rol JDBC dedicado, con sufijo de proyecto opcional para Session pooler.");
            Path ca = Path.of(variableObligatoria("DB_SSL_ROOT_CERT")).toAbsolutePath().normalize();
            exigir(Files.isRegularFile(ca) && Files.isReadable(ca), "Se requiere un archivo CA legible.");

            var configuracion = new PersistenciaConfiguracion();
            pool = configuracion.dataSource(url, username, variableObligatoria("DB_PASSWORD"));
            pool.setMaximumPoolSize(1);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(10000);
            pool.setValidationTimeout(5000);
            pool.setIdleTimeout(600000);
            pool.setMaxLifetime(1800000);
            pool.setReadOnly(true);
            pool.addDataSourceProperty("readOnlyMode", "always");
            pool.addDataSourceProperty("sslmode", "verify-full");
            pool.addDataSourceProperty("sslrootcert", ca.toString());
            jdbc = new NamedParameterJdbcTemplate(pool);
            JdbcTransactionManager gestor = configuracion.jdbcTransactionManager(pool);
            ejecutor = new EjecutorRlsJdbc(new UsuarioAutenticado(), gestor, jdbc);
            pedidos = new RepositorioPedidosJdbc(jdbc, ejecutor);
            usuarios = new RepositorioUsuariosJdbc(jdbc, ejecutor);
            // El sufijo del pooler enruta; PostgreSQL confirma el rol real y sus restricciones.
            exigir(baseLimpia(), "Rol JDBC, privilegios, solo lectura o estado inicial no seguros.");
        });
    }

    @AfterEach
    void limpiarIdentidadSintetica() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    void cerrarPool() {
        SecurityContextHolder.clearContext();
        protegido(() -> {
            if (pool != null) pool.close();
        });
    }

    protected boolean contextoCorrecto(UUID id) {
        exigir(verdadero(CONTEXTO_CORRECTO, id), "Rol, identidad, claims, solo lectura o RLS incorrectos.");
        return true;
    }

    protected boolean verdadero(String sql, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id.toString()), Boolean.class));
    }

    protected boolean baseLimpia() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate().queryForObject(BASE_LIMPIA, Boolean.class));
    }

    protected static <T> T conIdentidad(UUID id, Supplier<T> operacion) {
        var contexto = SecurityContextHolder.createEmptyContext();
        Jwt jwt = Jwt.withTokenValue("identidad-sintetica-no-es-un-jwt-real")
                .header("alg", "ES256").subject(id.toString()).claim("role", "authenticated").build();
        contexto.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(contexto);
        try {
            return operacion.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static boolean usuarioConexionPermitido(String username) {
        return ROL_JDBC.equals(username)
                || (username != null && username.matches(ROL_JDBC + "\\.[a-z0-9]+"));
    }

    private static String variableObligatoria(String nombre) {
        String valor = System.getenv(nombre);
        exigir(valor != null && !valor.isBlank(), "Falta configuracion externa obligatoria para integracion JDBC.");
        return valor;
    }

    protected static UUID uuidExterno(String valor) {
        exigir(valor != null && !valor.isBlank(), "Configurar ambos UUID para la prueba A/B.");
        UUID id = UUID.fromString(valor);
        exigir(id.toString().equalsIgnoreCase(valor), "Un UUID de la prueba A/B no es canonico.");
        return id;
    }

    protected static void esperarRechazo(Class<? extends RuntimeException> tipo, Runnable operacion) {
        try {
            operacion.run();
        } catch (RuntimeException fallo) {
            exigir(tipo.isInstance(fallo), "La operacion fallo por una causa distinta a la prevista.");
            return;
        }
        throw new FalloIntegracion("No se rechazo la operacion prevista.");
    }

    protected static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new FalloIntegracion(mensaje);
    }

    protected static void protegido(Runnable operacion) {
        try {
            operacion.run();
        } catch (TestAbortedException omitida) {
            throw omitida;
        } catch (FalloIntegracion fallo) {
            throw fallo; // Solo mensajes constantes de nuestras comprobaciones booleanas.
        } catch (Throwable fallo) {
            // Nunca adjuntar causas del driver, URL, credenciales ni datos de filas.
            throw new AssertionError("Fallo de integracion JDBC/RLS; revisar configuracion externa y permisos.");
        }
    }

    private static final class FalloIntegracion extends AssertionError {
        FalloIntegracion(String mensaje) {
            super(mensaje);
        }
    }
}
