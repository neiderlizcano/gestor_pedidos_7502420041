package com.neider.gestorpedidos.pedidos.aplicacion.soporte;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.neider.gestorpedidos.pedidos.aplicacion.puerto.salida.RepositorioPedidos;
import com.neider.gestorpedidos.pedidos.dominio.Pedido;

/**
 * Repositorio simulado que aplica el mismo filtro por propietario que debera
 * aplicar la implementacion real.
 */
public final class RepositorioPedidosEnMemoria implements RepositorioPedidos {

    private final Map<Long, Pedido> pedidos = new LinkedHashMap<>();
    private long secuencia;

    @Override
    public Pedido crear(Pedido pedido) {
        long id = ++secuencia;
        Pedido guardado = pedido.asignarId(id);
        pedidos.put(id, guardado);
        return guardado;
    }

    @Override
    public Optional<Pedido> buscarPorIdYPropietario(Long id, UUID propietarioId) {
        return Optional.ofNullable(pedidos.get(id))
                .filter(pedido -> pedido.perteneceA(propietarioId));
    }

    @Override
    public List<Pedido> listarPorPropietario(UUID propietarioId) {
        return pedidos.values().stream()
                .filter(pedido -> pedido.perteneceA(propietarioId))
                .toList();
    }

    @Override
    public Optional<Pedido> actualizarPorIdYPropietario(Long id, UUID propietarioId, Pedido pedido) {
        Pedido existente = pedidos.get(id);
        if (existente != null && existente.perteneceA(propietarioId)) {
            pedidos.put(id, pedido);
            return Optional.of(pedido);
        }
        return Optional.empty();
    }

    @Override
    public boolean eliminarPorIdYPropietario(Long id, UUID propietarioId) {
        Pedido pedido = pedidos.get(id);
        if (pedido != null && pedido.perteneceA(propietarioId)) {
            pedidos.remove(id);
            return true;
        }
        return false;
    }

    /**
     * Guarda un pedido ya persistido, con su identificador, para preparar un
     * escenario de prueba.
     */
    public void sembrar(Pedido pedido) {
        if (pedido.id() == null) {
            throw new IllegalArgumentException("El pedido sembrado debe tener identificador.");
        }
        pedidos.put(pedido.id(), pedido);
        secuencia = Math.max(secuencia, pedido.id());
    }

    /**
     * Devuelve un pedido sin filtrar por propietario; solo para comprobar el
     * estado interno en las pruebas.
     */
    public Optional<Pedido> buscarSinFiltrar(Long id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    public int total() {
        return pedidos.size();
    }
}
