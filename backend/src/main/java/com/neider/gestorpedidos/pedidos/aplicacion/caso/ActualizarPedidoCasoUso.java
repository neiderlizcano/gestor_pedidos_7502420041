package com.neider.gestorpedidos.pedidos.aplicacion.caso;

import java.util.Objects;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.error.PedidoNoEncontradoException;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.ActualizarPedido;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.entrada.DatosPedidoComando;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.IdentidadAutenticada;
import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;
import com.neider.gestorpedidos.pedidos.dominio.PedidoInvalidoException;

/**
 * Caso de uso que actualiza un pedido propio.
 *
 * <p>El identificador y el propietario se conservan; el propietario procede
 * del puerto de identidad.</p>
 */
public final class ActualizarPedidoCasoUso implements ActualizarPedido {

    private final RepositorioPedidos repositorio;
    private final IdentidadAutenticada identidad;

    public ActualizarPedidoCasoUso(RepositorioPedidos repositorio,
            IdentidadAutenticada identidad) {
        this.repositorio = Objects.requireNonNull(repositorio,
                "El repositorio de pedidos es obligatorio.");
        this.identidad = Objects.requireNonNull(identidad,
                "El puerto de identidad es obligatorio.");
    }

    @Override
    public Pedido ejecutar(Long id, DatosPedidoComando comando) {
        if (comando == null) {
            throw new PedidoInvalidoException("Los datos del pedido son obligatorios.");
        }

        UUID propietario = identidad.usuarioRequerido();
        Pedido existente = repositorio.buscarPorIdYPropietario(id, propietario)
                .orElseThrow(PedidoNoEncontradoException::new);

        Pedido cambios = new Pedido(
                existente.id(),
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

        Pedido actualizado = existente.actualizarCon(cambios);
        return repositorio.actualizarPorIdYPropietario(id, propietario, actualizado)
                .orElseThrow(PedidoNoEncontradoException::new);
    }
}
