package com.neider.gestorpedidos.usuarios.infraestructura.salida.jdbc;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.configuracion.persistencia.EjecutorRlsJdbc;
import com.neider.gestorpedidos.usuarios.aplicacion.puerto.salida.EliminarCuenta;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Referencia del diseno anterior: NO es un bean ni una alternativa activa. */
@Deprecated
public class EliminarCuentaJdbc implements EliminarCuenta {
    private final NamedParameterJdbcTemplate jdbc;
    private final EjecutorRlsJdbc rls;

    public EliminarCuentaJdbc(NamedParameterJdbcTemplate jdbc, EjecutorRlsJdbc rls) {
        this.jdbc = Objects.requireNonNull(jdbc, "El acceso JDBC es obligatorio.");
        this.rls = Objects.requireNonNull(rls, "El ejecutor RLS es obligatorio.");
        rls.verificarDataSource(jdbc);
    }

    @Override
    public void eliminar(UUID autenticado) {
        Objects.requireNonNull(autenticado, "La identidad es obligatoria.");
        rls.ejecutar(autenticado, () -> {
            // Sin UUID SQL: la funcion obtiene exclusivamente auth.uid().
            jdbc.queryForObject("SELECT private.delete_my_account()", Map.of(), (rs, fila) -> Boolean.TRUE);
            return null;
        });
        // ejecutar retorna solo despues del commit; tambien propaga sus errores.
    }
}
