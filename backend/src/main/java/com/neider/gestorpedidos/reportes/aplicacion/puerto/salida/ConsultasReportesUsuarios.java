package com.neider.gestorpedidos.reportes.aplicacion.puerto.salida;

import java.util.List;
import java.util.UUID;

import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroCorreo;
import com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada.FiltroNombreUsername;
import com.neider.gestorpedidos.usuarios.dominio.Usuario;

/** Consultas acotadas al perfil propio: como maximo una fila por UUID. */
public interface ConsultasReportesUsuarios {

    List<Usuario> porNombreUsername(UUID propietario, FiltroNombreUsername filtro);

    List<Usuario> porCorreo(UUID propietario, FiltroCorreo filtro);
}
