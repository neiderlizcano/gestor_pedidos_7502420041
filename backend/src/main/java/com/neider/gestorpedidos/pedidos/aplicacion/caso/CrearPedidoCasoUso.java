package com.neider.gestorpedidos.pedidos.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.CrearPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.DatosPedidoComando;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.pedidos.dominio.PedidoInvalidoException;

/**
 * Caso de uso que registra un pedido a nombre del usuario autenticado.
 */
public final class CrearPedidoCasoUso implements CrearPedido {

    private final RepositorioPedidos repositorio;
    private final IdentidadAutenticada identidad;

    public CrearPedidoCasoUso(RepositorioPedidos repositorio, IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de pedidos es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public Pedido ejecutar(DatosPedidoComando comando) {
        if (comando == null) {
            throw new PedidoInvalidoException("Los datos del pedido son obligatorios.");
        }

        UUID propietario = identidad.usuarioRequerido();
        Pedido pedido = new Pedido(
                null,
                propietario,
                comando.fecha(),
                comando.fechaEnvio(),
                comando.fechaEntrega(),
                comando.cliente(),
                comando.proveedor(),
                comando.valor(),
                comando.estado(),
                comando.pais(),
                comando.departamento(),
                comando.ciudad(),
                comando.nomenclaturaVivienda(),
                comando.propina());

        return repositorio.crear(pedido);
    }
}
