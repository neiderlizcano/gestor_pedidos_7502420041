package com.neider.gestorpedidos.reportes.infraestructura.salida.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.CoincidenciaPerfil;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportesUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("persistencia")
public class ConsultasReportesUsuariosJdbc implements ConsultasReportesUsuarios {

    private static final String SELECT_PROPIO = "SELECT id, username, nombre, email FROM public.usuarios WHERE id = :usuarioId";
    private static final RowMapper<Usuario> MAPEAR = ConsultasReportesUsuariosJdbc::mapear;
    private final NamedParameterJdbcTemplate jdbc;
    private final EjecutorRlsJdbc rls;

    public ConsultasReportesUsuariosJdbc(NamedParameterJdbcTemplate jdbc, EjecutorRlsJdbc rls) {
        this.jdbc = Objects.requireNonNull(jdbc, "El acceso JDBC a reportes es obligatorio.");
        this.rls = Objects.requireNonNull(rls, "El ejecutor RLS es obligatorio.");
        rls.verificarDataSource(jdbc);
    }

    @Override
    public List<Usuario> porNombreUsername(UUID propietario, FiltroNombreUsername filtro) {
        Objects.requireNonNull(filtro, "El filtro es obligatorio.");
        var parametros = parametros(propietario);
        String columna = switch (filtro.campo()) {
            case NOMBRE -> "nombre";
            case USERNAME -> "username";
        };
        String condicion;
        if (filtro.coincidencia() == CoincidenciaPerfil.EXACTA) {
            condicion = columna + " = :termino";
            parametros.addValue("termino", filtro.termino());
        } else {
            condicion = columna + " LIKE :termino ESCAPE '!'";
            // '!' es el escape fijo; '\' es texto ordinario con ESCAPE '!'. Solo el ultimo '%' es comodin.
            String literal = filtro.termino().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parametros.addValue("termino", literal + "%");
        }
        return rls.ejecutar(propietario, () -> jdbc.query(SELECT_PROPIO + " AND " + condicion, parametros, MAPEAR));
    }

    @Override
    public List<Usuario> porCorreo(UUID propietario, FiltroCorreo filtro) {
        Objects.requireNonNull(filtro, "El filtro es obligatorio.");
        var parametros = parametros(propietario).addValue("correo", filtro.correo());
        return rls.ejecutar(propietario, () -> jdbc.query(SELECT_PROPIO + " AND email = :correo", parametros, MAPEAR));
    }

    private static MapSqlParameterSource parametros(UUID propietario) {
        return new MapSqlParameterSource("usuarioId", Objects.requireNonNull(propietario, "El propietario es obligatorio."));
    }

    private static Usuario mapear(ResultSet fila, int numero) throws SQLException {
        return new Usuario(fila.getObject("id", UUID.class), fila.getString("username"),
                fila.getString("nombre"), fila.getString("email"));
    }
}
