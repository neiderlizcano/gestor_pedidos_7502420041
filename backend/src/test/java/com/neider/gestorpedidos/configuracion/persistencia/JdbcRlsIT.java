package com.neider.gestorpedidos.configuracion.persistencia;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.opentest4j.TestAbortedException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Integracion opcional, exclusivamente de lectura. No carga la aplicacion web
 * ni valida tokens: coloca una identidad sintetica en el contexto de pruebas.
 * Ningun inicializador estatico lee configuracion JDBC ni abre conexiones.
 */
@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class JdbcRlsIT extends SoporteJdbcRlsIT {

    private static final UUID SINTETICO_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SINTETICO_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
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

    private static final class RollbackLectura extends RuntimeException {
        RollbackLectura() {
            super("Rollback intencional de una transaccion de lectura.");
        }
    }
}
