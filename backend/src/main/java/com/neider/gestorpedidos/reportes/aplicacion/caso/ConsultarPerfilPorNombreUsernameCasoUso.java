package com.neider.gestorpedidos.reportes.aplicacion.caso;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.reportes.aplicacion.error.ConsultaReporteInvalidaException;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.ConsultarPerfilPorNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.ConsultasReportesUsuarios;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

public final class ConsultarPerfilPorNombreUsernameCasoUso implements ConsultarPerfilPorNombreUsername {

    private final ConsultasReportesUsuarios consultas;
    private final IdentidadAutenticada identidad;

    public ConsultarPerfilPorNombreUsernameCasoUso(ConsultasReportesUsuarios consultas, IdentidadAutenticada identidad) {
        this.consultas = Objects.requireNonNull(consultas, "El puerto de reportes es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad, "El puerto de identidad es obligatorio.");
    }

    @Override
    public List<Usuario> ejecutar(FiltroNombreUsername filtro) {
        UUID propietario = identidad.usuarioRequerido();
        if (filtro == null) throw new ConsultaReporteInvalidaException();
        return PerfilesPropios.verificar(consultas.porNombreUsername(propietario, filtro), propietario);
    }
}
