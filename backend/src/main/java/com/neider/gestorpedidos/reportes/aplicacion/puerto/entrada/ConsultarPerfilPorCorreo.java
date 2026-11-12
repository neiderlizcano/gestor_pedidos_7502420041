package com.neider.gestorpedidos.reportes.aplicacion.puerto.entrada;

import java.util.List;

import com.neider.gestorpedidos.usuarios.dominio.Usuario;

@FunctionalInterface
public interface ConsultarPerfilPorCorreo {

    List<Usuario> ejecutar(FiltroCorreo filtro);
}
