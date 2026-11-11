package com.neider.gestorpedidos.configuracion.persistencia;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.neider.gestorpedidos.configuracion.seguridad.UsuarioAutenticado;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Contexto RLS local a una transaccion, sin conexiones manuales ni estado de sesion. */
@Component
@Profile("persistencia")
public class EjecutorRlsJdbc {

    static final String ESTADO_BASE = """
            SELECT current_role::text AS rol_actual, session_user::text AS rol_sesion,
                current_setting('role', true) AS rol_configurado,
                current_setting('request.jwt.claim.sub', true) AS sub,
                current_setting('request.jwt.claims', true) AS claims,
                current_setting('request.jwt.claim.role', true) AS rol_claim,
                current_setting('row_security') AS rls,
                (SELECT NOT (r.rolsuper OR r.rolbypassrls OR r.rolinherit
                    OR r.rolcreaterole OR r.rolcreatedb OR r.rolreplication)
                    AND NOT EXISTS (
                        SELECT 1 FROM pg_catalog.pg_class c
                        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public' AND c.relname IN ('usuarios', 'pedidos')
                            AND c.relowner = r.oid)
                 FROM pg_catalog.pg_roles r WHERE r.rolname = session_user) AS login_seguro
            """;

    static final String ROL_LOCAL = "SET LOCAL ROLE authenticated";
    static final String SUB_LOCAL = "SELECT set_config('request.jwt.claim.sub', ?, true)";
    static final String CLAIMS_LOCAL = "SELECT set_config('request.jwt.claims', ?, true)";

    static final String ESTADO_RLS = """
            SELECT current_role::text AS rol_actual, auth.uid() AS usuario,
                current_setting('row_security') AS rls,
                (row_security_active('public.usuarios'::regclass)
                 AND row_security_active('public.pedidos'::regclass)
                 AND (SELECT NOT (rolsuper OR rolbypassrls)
                      FROM pg_catalog.pg_roles WHERE rolname = current_role)) AS tablas_protegidas
            """;

    private static final RowMapper<EstadoBase> MAPEAR_BASE = (rs, fila) -> new EstadoBase(
            rs.getString("rol_actual"), rs.getString("rol_sesion"), rs.getString("rol_configurado"),
            rs.getString("sub"), rs.getString("claims"), rs.getString("rol_claim"),
            rs.getString("rls"), rs.getBoolean("login_seguro"));
    private static final RowMapper<EstadoRls> MAPEAR_RLS = (rs, fila) -> new EstadoRls(
            rs.getString("rol_actual"), rs.getObject("usuario", UUID.class),
            rs.getString("rls"), rs.getBoolean("tablas_protegidas"));

    private final UsuarioAutenticado usuario;
    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final AtomicBoolean bloqueado = new AtomicBoolean();

    public EjecutorRlsJdbc(UsuarioAutenticado usuario, JdbcTransactionManager gestor,
            NamedParameterJdbcTemplate consultas) {
        this.usuario = Objects.requireNonNull(usuario, "La fuente de identidad es obligatoria.");
        Objects.requireNonNull(gestor, "El gestor transaccional es obligatorio.");
        if (!(gestor.getDataSource() instanceof HikariDataSource hikari)) {
            throw new IllegalStateException("RLS requiere el DataSource Hikari configurado.");
        }
        this.dataSource = hikari;
        verificarDataSource(consultas);
        this.jdbc = consultas.getJdbcTemplate();
        this.transaccion = new TransactionTemplate(gestor);
    }

    /** Impide que un adaptador use un DataSource distinto al del contexto RLS. */
    public void verificarDataSource(NamedParameterJdbcTemplate consultas) {
        if (Objects.requireNonNull(consultas, "El acceso JDBC es obligatorio.")
                .getJdbcTemplate().getDataSource() != dataSource) {
            throw new IllegalStateException("La transaccion RLS y las consultas deben compartir DataSource.");
        }
    }

    public <T> T ejecutar(UUID propietario, Supplier<T> operacion) {
        if (bloqueado.get()) {
            throw new IllegalStateException("Persistencia bloqueada: no se pudo garantizar el estado del pool.");
        }
        UUID autenticado = usuario.usuarioActual();
        if (autenticado == null) {
            throw new AuthenticationCredentialsNotFoundException("Se requiere autenticacion JWT.");
        }
        if (!autenticado.equals(propietario)) {
            throw new AccessDeniedException("El propietario no coincide con la identidad autenticada.");
        }
        Objects.requireNonNull(operacion, "La operacion JDBC es obligatoria.");
        // No participar ni suspender transacciones externas; tampoco se permite reentrada.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("No se permiten transacciones o conexiones externas preexistentes.");
        }

        try {
            return transaccion.execute(estado -> {
                if (!estado.isNewTransaction()) {
                    throw new IllegalStateException("RLS requiere una transaccion propia.");
                }
                EstadoBase base = jdbc.queryForObject(ESTADO_BASE, MAPEAR_BASE);
                if (base == null || !base.seguro()) {
                    throw new IllegalStateException("La conexion presenta un contexto de seguridad inseguro.");
                }
                jdbc.execute(ROL_LOCAL);
                String sub = autenticado.toString();
                // UUID tipado y rol fijo: no se copian claims arbitrarios ni el token original.
                String claims = "{\"sub\":\"" + sub + "\",\"role\":\"authenticated\"}";
                if (!sub.equals(jdbc.queryForObject(SUB_LOCAL, String.class, sub))
                        || !claims.equals(jdbc.queryForObject(CLAIMS_LOCAL, String.class, claims))) {
                    throw new IllegalStateException("No se pudo establecer la identidad RLS local.");
                }
                EstadoRls rls = jdbc.queryForObject(ESTADO_RLS, MAPEAR_RLS);
                if (rls == null || !rls.seguro(autenticado)) {
                    throw new IllegalStateException("No se pudo verificar el contexto RLS de la transaccion.");
                }
                return operacion.get();
            });
        } catch (RuntimeException | Error fallo) {
            // TransactionTemplate ya intento rollback. Expulsion conservadora de todo el pool:
            // las conexiones prestadas se descartan al devolverse, sin adquirirlas manualmente.
            try {
                var pool = dataSource.getHikariPoolMXBean();
                if (pool == null) {
                    throw new IllegalStateException("Pool no disponible.");
                }
                pool.softEvictConnections();
            } catch (RuntimeException | Error falloExpulsion) {
                bloqueado.set(true);
            }
            throw fallo;
        }
    }

    record EstadoBase(String rolActual, String rolSesion, String rolConfigurado, String sub,
            String claims, String rolClaim, String rls, boolean loginSeguro) {
        boolean seguro() {
            return rolSesion != null && rolSesion.equals(rolActual)
                    && !"authenticated".equals(rolActual) && !"postgres".equals(rolActual)
                    && !"service_role".equals(rolActual) && "none".equals(rolConfigurado)
                    && vacio(sub) && vacio(claims) && vacio(rolClaim) && "on".equals(rls) && loginSeguro;
        }

        private static boolean vacio(String valor) {
            return valor == null || valor.isEmpty();
        }
    }

    record EstadoRls(String rolActual, UUID usuario, String rls, boolean tablasProtegidas) {
        boolean seguro(UUID esperado) {
            return "authenticated".equals(rolActual) && esperado.equals(usuario)
                    && "on".equals(rls) && tablasProtegidas;
        }
    }
}
