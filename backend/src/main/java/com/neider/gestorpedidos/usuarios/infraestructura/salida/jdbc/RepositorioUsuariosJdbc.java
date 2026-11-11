package com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.RepositorioUsuarios;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Persistencia del perfil propio. El UUID y el correo de creacion proceden de
 * la identidad confiable que utilizan los casos de uso, nunca se generan aqui.
 */
@Repository
@Profile("persistencia")
public class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    private static final String CREAR = """
            INSERT INTO public.usuarios (id, username, nombre, email)
            VALUES (:id, :username, :nombre, :email)
            ON CONFLICT DO NOTHING
            RETURNING id, username, nombre, email
            """;

    private static final String BUSCAR = """
            SELECT id, username, nombre, email
            FROM public.usuarios
            WHERE id = :id
            """;

    private static final String ACTUALIZAR = """
            UPDATE public.usuarios
            SET username = :username, nombre = :nombre
            WHERE id = :id
            RETURNING id, username, nombre, email
            """;

    private static final RowMapper<Usuario> MAPPER = RepositorioUsuariosJdbc::mapear;

    private final NamedParameterJdbcTemplate jdbc;
    private final EjecutorRlsJdbc rls;

    public RepositorioUsuariosJdbc(NamedParameterJdbcTemplate jdbc, EjecutorRlsJdbc rls) {
        this.jdbc = Objects.requireNonNull(jdbc, "El acceso JDBC a usuarios es obligatorio.");
        this.rls = Objects.requireNonNull(rls, "El ejecutor RLS es obligatorio.");
        rls.verificarDataSource(jdbc);
    }

    @Override
    public Optional<Usuario> crear(Usuario perfil) {
        Objects.requireNonNull(perfil, "El perfil es obligatorio.");
        MapSqlParameterSource parametros = datosEditables(perfil)
                .addValue("email", perfil.email());
        // El conflicto de UUID o correo no sobrescribe datos ni consulta perfiles ajenos.
        // Los demas errores (FK de Auth, RLS, conexion, etc.) se propagan como errores JDBC.
        return rls.ejecutar(perfil.id(), () -> DataAccessUtils.optionalResult(jdbc.query(CREAR, parametros, MAPPER)));
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("id",
                Objects.requireNonNull(id, "El identificador del usuario es obligatorio."));
        return rls.ejecutar(id, () -> DataAccessUtils.optionalResult(jdbc.query(BUSCAR, parametros, MAPPER)));
    }

    @Override
    public Optional<Usuario> actualizar(Usuario perfil) {
        Objects.requireNonNull(perfil, "El perfil es obligatorio.");
        // UUID solo en WHERE; el correo se lee de PostgreSQL, nunca se actualiza.
        return rls.ejecutar(perfil.id(),
                () -> DataAccessUtils.optionalResult(jdbc.query(ACTUALIZAR, datosEditables(perfil), MAPPER)));
    }

    private static MapSqlParameterSource datosEditables(Usuario perfil) {
        return new MapSqlParameterSource()
                .addValue("id", perfil.id())
                .addValue("username", perfil.username())
                .addValue("nombre", perfil.nombre());
    }

    private static Usuario mapear(ResultSet resultado, int fila) throws SQLException {
        return new Usuario(
                resultado.getObject("id", UUID.class),
                resultado.getString("username"),
                resultado.getString("nombre"),
                resultado.getString("email"));
    }
}
