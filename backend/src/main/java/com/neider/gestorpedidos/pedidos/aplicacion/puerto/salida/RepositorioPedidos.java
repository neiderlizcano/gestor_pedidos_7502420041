package com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Contrato de persistencia de pedidos.
 *
 * <p>Las operaciones de lectura, actualizacion y eliminacion deben filtrar por
 * el identificador del pedido y el propietario para impedir el acceso a
 * pedidos ajenos.</p>
 */
public interface RepositorioPedidos {

    /**
     * Persiste un pedido nuevo y devuelve la instancia con el identificador
     * generado.
     */
    Pedido crear(Pedido pedido);

    /**
     * Busca un pedido por identificador solo si pertenece al propietario
     * indicado.
     */
    Optional<Pedido> buscarPorIdYPropietario(Long id, UUID propietarioId);

    /**
     * Devuelve los pedidos del propietario indicado.
     */
    List<Pedido> listarPorPropietario(UUID propietarioId);

    /**
     * Actualiza el pedido indicado solo si pertenece al propietario.
     *
     * @return el pedido actualizado, o vacio si no existe o no pertenece al
     *         propietario.
     */
    Optional<Pedido> actualizarPorIdYPropietario(Long id, UUID propietarioId, Pedido pedido);

    /**
     * Elimina el pedido solo si pertenece al propietario indicado.
     *
     * @return {@code true} si se elimino; {@code false} si no existia o no
     *         pertenecia al propietario.
     */
    boolean eliminarPorIdYPropietario(Long id, UUID propietarioId);
}
