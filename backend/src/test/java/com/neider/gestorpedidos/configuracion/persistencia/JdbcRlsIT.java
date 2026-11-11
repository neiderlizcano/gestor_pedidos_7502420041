package com.neider.gestorpedidos.configuracion.persistencia;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.neider.gestorpedidos.pedidos.infraestructura.salida.jdbc.RepositorioPedidosJdbc;
import com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc.RepositorioUsuariosJdbc;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opentest4j.TestAbortedException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Integracion opcional, exclusivamente de lectura. No carga la aplicacion web
 * ni valida tokens: coloca una identidad sintetica en el contexto de pruebas.
 * Ningun inicializador estatico lee configuracion JDBC ni abre conexiones.
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class JdbcRlsIT {

    private static final UUID SINTETICO_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SINTETICO_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
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

    private HikariDataSource pool;
    private NamedParameterJdbcTemplate jdbc;
    private EjecutorRlsJdbc ejecutor;
    private RepositorioPedidosJdbc pedidos;
    private RepositorioUsuariosJdbc usuarios;

    @BeforeAll
    void prepararSoloConAutorizacionExplicita() {
        if (!"true".equals(System.getenv("RUN_DB_INTEGRATION"))) {
            throw new TestAbortedException("Integracion JDBC desactivada.");
        }
        // Tambien proteger la ejecucion explicita desde un IDE; sin tocar logs de produccion.
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
            // El sufijo del pooler solo enruta la conexion: PostgreSQL debe confirmar el rol real.
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
            if (pool != null) {
                pool.close();
            }
        });
    }

    @Test
    void verificaRolAuthUidClaimsYRlsSinAlterarRegistros() {
        protegido(() -> {
            conIdentidad(SINTETICO_A, () -> ejecutor.ejecutar(SINTETICO_A, () -> contextoCorrecto(SINTETICO_A)));
            exigir(baseLimpia(), "El contexto no se restauro tras commit.");
        });
    }

    @Test
    void politicasNoExponenFilasDeOtrosUsuariosYLosAdaptadoresSoloLeen() {
        protegido(() -> conIdentidad(SINTETICO_A, () -> {
            ejecutor.ejecutar(SINTETICO_A, () -> {
                contextoCorrecto(SINTETICO_A);
                // Deliberadamente sin filtro de propietario en la consulta exterior: prueba RLS.
                exigir(verdadero("""
                        SELECT NOT EXISTS (SELECT 1 FROM public.usuarios WHERE id <> CAST(:id AS uuid))
                            AND NOT EXISTS (SELECT 1 FROM public.pedidos WHERE usuario_id <> CAST(:id AS uuid))
                        """, SINTETICO_A), "RLS expuso filas de otro propietario.");
                return true;
            });
            exigir(usuarios.buscarPorId(SINTETICO_A).map(u -> SINTETICO_A.equals(u.id())).orElse(true),
                    "El adaptador de usuarios devolvio otra identidad.");
            exigir(pedidos.listarPorPropietario(SINTETICO_A).stream()
                    .allMatch(p -> SINTETICO_A.equals(p.usuarioId())), "El adaptador de pedidos expuso otro propietario.");
            exigir(pedidos.buscarPorIdYPropietario(Long.MIN_VALUE, SINTETICO_A)
                    .map(p -> SINTETICO_A.equals(p.usuarioId())).orElse(true),
                    "La consulta por identificador expuso otro propietario.");
            return true;
        }));
    }

    @Test
    void sinIdentidadODiscordanteNoSeEjecutaLaConsulta() {
        protegido(() -> {
            SecurityContextHolder.clearContext();
            var consultado = new AtomicBoolean();
            esperarRechazo(AuthenticationCredentialsNotFoundException.class,
                    () -> ejecutor.ejecutar(SINTETICO_A, () -> { consultado.set(true); return true; }));
            esperarRechazo(AuthenticationCredentialsNotFoundException.class, () -> usuarios.buscarPorId(SINTETICO_A));
            esperarRechazo(AuthenticationCredentialsNotFoundException.class,
                    () -> pedidos.listarPorPropietario(SINTETICO_A));
            conIdentidad(SINTETICO_A, () -> {
                esperarRechazo(AccessDeniedException.class,
                        () -> ejecutor.ejecutar(SINTETICO_B, () -> { consultado.set(true); return true; }));
                esperarRechazo(AccessDeniedException.class, () -> usuarios.buscarPorId(SINTETICO_B));
                esperarRechazo(AccessDeniedException.class, () -> pedidos.listarPorPropietario(SINTETICO_B));
                return true;
            });
            exigir(!consultado.get(), "Se ejecuto una operacion sin autorizacion.");
        });
    }

    @Test
    void rollbackDeLecturaRestauraContextoYPermiteUnaOperacionPosterior() {
        protegido(() -> {
            conIdentidad(SINTETICO_A, () -> {
                esperarRechazo(RollbackLectura.class, () -> ejecutor.ejecutar(SINTETICO_A, () -> {
                    contextoCorrecto(SINTETICO_A);
                    throw new RollbackLectura();
                }));
                return true;
            });
            // El ejecutor expulsa conexiones tras errores; no se exige reutilizar la fallida.
            exigir(baseLimpia(), "El contexto no esta limpio tras rollback y expulsion.");
            conIdentidad(SINTETICO_B, () -> ejecutor.ejecutar(SINTETICO_B, () -> contextoCorrecto(SINTETICO_B)));
            exigir(pool.getHikariPoolMXBean().getTotalConnections() <= 1, "El pool excedio una conexion.");
        });
    }

    @Test
    void commitReutilizaUnaConexionSinTransferirIdentidad() {
        protegido(() -> {
            Integer primera = conIdentidad(SINTETICO_A, () -> ejecutor.ejecutar(SINTETICO_A, () -> {
                contextoCorrecto(SINTETICO_A);
                return jdbc.getJdbcTemplate().queryForObject("SELECT pg_backend_pid()", Integer.class);
            }));
            exigir(baseLimpia(), "Persisten claims tras la primera operacion.");
            Integer segunda = conIdentidad(SINTETICO_B, () -> ejecutor.ejecutar(SINTETICO_B, () -> {
                contextoCorrecto(SINTETICO_B);
                return jdbc.getJdbcTemplate().queryForObject("SELECT pg_backend_pid()", Integer.class);
            }));
            exigir(primera != null && primera.equals(segunda),
                    "No se reutilizo el backend: revisar modalidad de conexion o pooler.");
            exigir(baseLimpia(), "Persisten claims tras la segunda operacion.");
        });
    }

    @Test
    void aislamientoABConPerfilesYPedidosPreexistentesOpcionales() {
        protegido(() -> {
            String entradaA = System.getenv("DB_TEST_USER_A");
            String entradaB = System.getenv("DB_TEST_USER_B");
            if (entradaA == null && entradaB == null) {
                throw new TestAbortedException("A/B omitida: configurar ambos UUID de perfiles preexistentes.");
            }
            // Solo memoria del proceso: no escribir UUID, identificadores ni filas en informes, logs o archivos.
            UUID a = uuidExterno(entradaA);
            UUID b = uuidExterno(entradaB);
            exigir(!a.equals(b), "A/B requiere identidades distintas.");
            Long pedidoA = conIdentidad(a, () -> ejecutor.ejecutar(a, () -> {
                comprobarPerfilPropioYAislamiento(a, b);
                Long id = jdbc.queryForObject("""
                        SELECT min(id) FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid)
                        """, new MapSqlParameterSource("id", a.toString()), Long.class);
                exigir(id != null, "A/B requiere al menos un pedido propio visible para A.");
                return id;
            }));
            exigir(baseLimpia(), "Persisten claims tras la lectura A de la prueba A/B.");

            conIdentidad(b, () -> ejecutor.ejecutar(b, () -> {
                comprobarPerfilPropioYAislamiento(b, a);
                // Sin filtro de propietario: debe ser RLS quien oculte el pedido conocido de A.
                exigir(Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT NOT EXISTS (SELECT 1 FROM public.pedidos WHERE id = :pedido)
                        """, new MapSqlParameterSource("pedido", pedidoA), Boolean.class)),
                        "RLS permitio a B consultar el pedido identificado bajo A.");
                return true;
            }));
            exigir(baseLimpia(), "Persisten claims tras la lectura B de la prueba A/B.");
        });
    }

    private void comprobarPerfilPropioYAislamiento(UUID propio, UUID ajeno) {
        contextoCorrecto(propio);
        exigir(verdadero("""
                SELECT EXISTS (SELECT 1 FROM public.usuarios WHERE id = CAST(:id AS uuid))
                """, propio), "A/B requiere un perfil propio visible por identidad.");
        exigir(verdadero("""
                SELECT NOT EXISTS (SELECT 1 FROM public.usuarios WHERE id = CAST(:id AS uuid))
                    AND NOT EXISTS (SELECT 1 FROM public.pedidos WHERE usuario_id = CAST(:id AS uuid))
                """, ajeno), "RLS permitio acceso cruzado A/B.");
    }

    private boolean contextoCorrecto(UUID id) {
        exigir(verdadero(CONTEXTO_CORRECTO, id), "Rol, identidad, claims, solo lectura o RLS incorrectos.");
        return true;
    }

    private boolean verdadero(String sql, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new MapSqlParameterSource("id", id.toString()), Boolean.class));
    }

    private boolean baseLimpia() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate().queryForObject(BASE_LIMPIA, Boolean.class));
    }

    private static <T> T conIdentidad(UUID id, Supplier<T> operacion) {
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

    private static UUID uuidExterno(String valor) {
        exigir(valor != null && !valor.isBlank(), "Configurar ambos UUID para la prueba A/B.");
        UUID id = UUID.fromString(valor);
        exigir(id.toString().equalsIgnoreCase(valor), "Un UUID de la prueba A/B no es canonico.");
        return id;
    }

    private static void esperarRechazo(Class<? extends RuntimeException> tipo, Runnable operacion) {
        try {
            operacion.run();
        } catch (RuntimeException fallo) {
            exigir(tipo.isInstance(fallo), "La operacion fallo por una causa distinta a la prevista.");
            return;
        }
        throw new FalloIntegracion("No se rechazo la operacion prevista.");
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new FalloIntegracion(mensaje);
        }
    }

    private static void protegido(Runnable operacion) {
        try {
            operacion.run();
        } catch (TestAbortedException omitida) {
            throw omitida;
        } catch (FalloIntegracion fallo) {
            throw fallo; // Solo nuestras aserciones booleanas, sin valores reales.
        } catch (Throwable fallo) {
            // No adjuntar causas del driver: pueden contener URL, credenciales o datos de filas.
            throw new AssertionError("Fallo de integracion JDBC/RLS; revisar configuracion externa y permisos.");
        }
    }

    private static final class FalloIntegracion extends AssertionError {
        FalloIntegracion(String mensaje) {
            super(mensaje);
        }
    }

    private static final class RollbackLectura extends RuntimeException {
        RollbackLectura() {
            super("Rollback intencional de una transaccion de lectura.");
        }
    }
}
