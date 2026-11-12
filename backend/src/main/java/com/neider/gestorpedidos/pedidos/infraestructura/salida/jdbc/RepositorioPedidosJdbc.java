package com.neider.gestorpedidos.pedidos.infraestructura.salida.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Adaptador PostgreSQL del puerto de pedidos.
 *
 * <p>Todas las operaciones sobre pedidos existentes filtran por identificador y
 * propietario. El propietario de una actualizacion nunca forma parte del
 * conjunto de columnas modificables.</p>
 */
@Repository
@Profile("persistencia")
public class RepositorioPedidosJdbc implements RepositorioPedidos {

    private static final String COLUMNAS = """
            id, usuario_id, fecha, fecha_envio, fecha_entrega, cliente, proveedor,
            valor, estado, pais, departamento, ciudad, nomenclatura_vivienda, propina
            """;

    private static final String INSERTAR = """
            INSERT INTO public.pedidos (
                usuario_id, fecha, fecha_envio, fecha_entrega, cliente, proveedor,
                valor, estado, pais, departamento, ciudad, nomenclatura_vivienda, propina)
            VALUES (
                :usuarioId, :fecha, :fechaEnvio, :fechaEntrega, :cliente, :proveedor,
                :valor, :estado, :pais, :departamento, :ciudad, :nomenclaturaVivienda, :propina)
            """;

    private static final String BUSCAR = "SELECT " + COLUMNAS
            + " FROM public.pedidos WHERE id = :id AND usuario_id = :usuarioId";

    private static final String LISTAR = "SELECT " + COLUMNAS
            + " FROM public.pedidos WHERE usuario_id = :usuarioId ORDER BY id";

    private static final String ACTUALIZAR = """
            UPDATE public.pedidos
            SET fecha = :fecha,
                fecha_envio = :fechaEnvio,
                fecha_entrega = :fechaEntrega,
                cliente = :cliente,
                proveedor = :proveedor,
                valor = :valor,
                estado = :estado,
                pais = :pais,
                departamento = :departamento,
                ciudad = :ciudad,
                nomenclatura_vivienda = :nomenclaturaVivienda,
                propina = :propina
            WHERE id = :id AND usuario_id = :usuarioId
            """;

    private static final String ELIMINAR = """
            DELETE FROM public.pedidos
            WHERE id = :id AND usuario_id = :usuarioId
            """;

    private static final RowMapper<Pedido> MAPPER = RepositorioPedidosJdbc::mapear;

    private final NamedParameterJdbcTemplate jdbc;
    private final EjecutorRlsJdbc rls;

    public RepositorioPedidosJdbc(NamedParameterJdbcTemplate jdbc, EjecutorRlsJdbc rls) {
        this.jdbc = Objects.requireNonNull(jdbc,
                "El acceso JDBC a pedidos es obligatorio.");
        this.rls = Objects.requireNonNull(rls, "El ejecutor RLS es obligatorio.");
        rls.verificarDataSource(jdbc);
    }

    @Override
    public Pedido crear(Pedido pedido) {
        Objects.requireNonNull(pedido, "El pedido es obligatorio.");
        if (pedido.id() != null) {
            throw new IllegalArgumentException(
                    "Un pedido nuevo no debe tener identificador asignado.");
        }

        return rls.ejecutar(pedido.usuarioId(), () -> {
            KeyHolder clave = new GeneratedKeyHolder();
            int filas = jdbc.update(INSERTAR, parametros(pedido), clave, new String[] {"id"});
            if (filas != 1) {
                throw new IllegalStateException("No se pudo registrar el pedido.");
            }
            Number identificador = clave.getKey();
            if (identificador == null) {
                throw new IllegalStateException("PostgreSQL no devolvio el identificador del pedido.");
            }
            return pedido.asignarId(identificador.longValue());
        });
    }

    @Override
    public Optional<Pedido> buscarPorIdYPropietario(Long id, UUID propietarioId) {
        return rls.ejecutar(propietarioId, () -> jdbc.query(BUSCAR, claves(id, propietarioId), MAPPER)
                .stream()
                .findFirst());
    }

    @Override
    public List<Pedido> listarPorPropietario(UUID propietarioId) {
        SqlParameterSource parametros = new MapSqlParameterSource(
                "usuarioId", propietarioObligatorio(propietarioId));
        return rls.ejecutar(propietarioId, () -> jdbc.query(LISTAR, parametros, MAPPER));
    }

    @Override
    public Optional<Pedido> actualizarPorIdYPropietario(Long id, UUID propietarioId, Pedido pedido) {
        Objects.requireNonNull(pedido, "El pedido es obligatorio.");
        if (!pedido.usuarioId().equals(propietarioObligatorio(propietarioId))) {
            throw new IllegalArgumentException(
                    "El propietario del pedido no coincide con el propietario autenticado.");
        }

        Pedido actualizado = conIdentificador(pedido, id);
        SqlParameterSource parametros = parametros(actualizado)
                .addValue("id", id)
                .addValue("usuarioId", propietarioId);

        return rls.ejecutar(propietarioId, () -> {
            int filas = jdbc.update(ACTUALIZAR, parametros);
            return filas > 0 ? Optional.of(actualizado) : Optional.empty();
        });
    }

    @Override
    public boolean eliminarPorIdYPropietario(Long id, UUID propietarioId) {
        return rls.ejecutar(propietarioId, () -> jdbc.update(ELIMINAR, claves(id, propietarioId)) > 0);
    }

    private static MapSqlParameterSource parametros(Pedido pedido) {
        return new MapSqlParameterSource()
                .addValue("usuarioId", pedido.usuarioId())
                .addValue("fecha", pedido.fecha())
                .addValue("fechaEnvio", pedido.fechaEnvio())
                .addValue("fechaEntrega", pedido.fechaEntrega())
                .addValue("cliente", pedido.cliente())
                .addValue("proveedor", pedido.proveedor())
                .addValue("valor", pedido.valor())
                .addValue("estado", pedido.estado())
                .addValue("pais", pedido.pais())
                .addValue("departamento", pedido.departamento())
                .addValue("ciudad", pedido.ciudad())
                .addValue("nomenclaturaVivienda", pedido.nomenclaturaVivienda())
                .addValue("propina", pedido.propina());
    }

    private static MapSqlParameterSource claves(Long id, UUID propietarioId) {
        return new MapSqlParameterSource()
                .addValue("id", Objects.requireNonNull(id,
                        "El identificador del pedido es obligatorio."))
                .addValue("usuarioId", propietarioObligatorio(propietarioId));
    }

    private static UUID propietarioObligatorio(UUID propietarioId) {
        return Objects.requireNonNull(propietarioId,
                "El propietario del pedido es obligatorio.");
    }

    private static Pedido conIdentificador(Pedido pedido, Long id) {
        Objects.requireNonNull(id, "El identificador del pedido es obligatorio.");
        if (pedido.id() == null) {
            return pedido.asignarId(id);
        }
        if (!pedido.id().equals(id)) {
            throw new IllegalArgumentException(
                    "El identificador del pedido no coincide con el pedido a actualizar.");
        }
        return pedido;
    }

    private static Pedido mapear(ResultSet resultado, int fila) throws SQLException {
        return new Pedido(
                resultado.getLong("id"),
                resultado.getObject("usuario_id", UUID.class),
                resultado.getObject("fecha", LocalDate.class),
                resultado.getObject("fecha_envio", LocalDate.class),
                resultado.getObject("fecha_entrega", LocalDate.class),
                resultado.getString("cliente"),
                resultado.getString("proveedor"),
                resultado.getBigDecimal("valor"),
                resultado.getString("estado"),
                resultado.getString("pais"),
                resultado.getString("departamento"),
                resultado.getString("ciudad"),
                resultado.getString("nomenclatura_vivienda"),
                resultado.getBigDecimal("propina"));
    }
}
