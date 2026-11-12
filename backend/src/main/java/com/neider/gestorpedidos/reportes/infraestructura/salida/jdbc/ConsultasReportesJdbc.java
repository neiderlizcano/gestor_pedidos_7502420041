package com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroEstadoFechas;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroUbicacion;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.PaginacionReporte;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportes;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Solo SELECT, parametros ligados y doble aislamiento: propietario explicito y ejecutor RLS obligatorio. */
@Repository
@Profile("persistencia")
public class ConsultasReportesJdbc implements ConsultasReportes {

    private static final String SELECT_PROPIO = """
            SELECT id, usuario_id, fecha, fecha_envio, fecha_entrega, cliente, proveedor,
                valor, estado, pais, departamento, ciudad, nomenclatura_vivienda, propina
            FROM public.pedidos WHERE usuario_id = :usuarioId
            """;
    private static final String PAGINAR = " ORDER BY id ASC LIMIT :limite OFFSET :desplazamiento";
    private static final RowMapper<Pedido> MAPEAR = ConsultasReportesJdbc::mapear;

    private final NamedParameterJdbcTemplate jdbc;
    private final EjecutorRlsJdbc rls;

    public ConsultasReportesJdbc(NamedParameterJdbcTemplate jdbc, EjecutorRlsJdbc rls) {
        this.jdbc = Objects.requireNonNull(jdbc, "El acceso JDBC a reportes es obligatorio.");
        this.rls = Objects.requireNonNull(rls, "El ejecutor RLS es obligatorio.");
        rls.verificarDataSource(jdbc);
    }

    @Override
    public List<Pedido> porEstadoFechas(UUID propietario, FiltroEstadoFechas filtros, PaginacionReporte paginacion) {
        Objects.requireNonNull(filtros, "Los filtros son obligatorios.");
        MapSqlParameterSource parametros = parametros(propietario, paginacion);
        StringBuilder sql = new StringBuilder(SELECT_PROPIO);
        // La unica parte variable del identificador SQL procede de este switch cerrado, nunca del HTTP.
        String columna = switch (filtros.fechaBase()) {
            case FECHA -> "fecha";
            case FECHA_ENVIO -> "fecha_envio";
            case FECHA_ENTREGA -> "fecha_entrega";
        };
        agregarTexto(sql, parametros, "estado", filtros.estado());
        if (filtros.desde() != null) {
            sql.append(" AND ").append(columna).append(" >= :desde");
            parametros.addValue("desde", filtros.desde(), Types.DATE);
        }
        if (filtros.hasta() != null) {
            sql.append(" AND ").append(columna).append(" <= :hasta");
            parametros.addValue("hasta", filtros.hasta(), Types.DATE);
        }
        return rls.ejecutar(propietario, () -> jdbc.query(sql + PAGINAR, parametros, MAPEAR));
    }

    @Override
    public List<Pedido> porUbicacion(UUID propietario, FiltroUbicacion filtros, PaginacionReporte paginacion) {
        Objects.requireNonNull(filtros, "Los filtros son obligatorios.");
        MapSqlParameterSource parametros = parametros(propietario, paginacion);
        StringBuilder sql = new StringBuilder(SELECT_PROPIO);
        agregarTexto(sql, parametros, "pais", filtros.pais());
        agregarTexto(sql, parametros, "departamento", filtros.departamento());
        agregarTexto(sql, parametros, "ciudad", filtros.ciudad());
        return rls.ejecutar(propietario, () -> jdbc.query(sql + PAGINAR, parametros, MAPEAR));
    }

    private static MapSqlParameterSource parametros(UUID propietario, PaginacionReporte paginacion) {
        Objects.requireNonNull(paginacion, "La paginacion es obligatoria.");
        return new MapSqlParameterSource("usuarioId", Objects.requireNonNull(propietario, "El propietario es obligatorio."))
                .addValue("limite", paginacion.tamano() + 1)
                .addValue("desplazamiento", paginacion.desplazamiento());
    }

    private static void agregarTexto(StringBuilder sql, MapSqlParameterSource parametros, String campo, String valor) {
        // campo solo se llama con literales internos; todos los valores externos se ligan como parametros.
        if (valor != null) {
            sql.append(" AND ").append(campo).append(" = :").append(campo);
            parametros.addValue(campo, valor);
        }
    }

    private static Pedido mapear(ResultSet fila, int numero) throws SQLException {
        return new Pedido(fila.getLong("id"), fila.getObject("usuario_id", UUID.class),
                fila.getObject("fecha", LocalDate.class), fila.getObject("fecha_envio", LocalDate.class),
                fila.getObject("fecha_entrega", LocalDate.class), fila.getString("cliente"),
                fila.getString("proveedor"), fila.getBigDecimal("valor"), fila.getString("estado"),
                fila.getString("pais"), fila.getString("departamento"), fila.getString("ciudad"),
                fila.getString("nomenclatura_vivienda"), fila.getBigDecimal("propina"));
    }
}
