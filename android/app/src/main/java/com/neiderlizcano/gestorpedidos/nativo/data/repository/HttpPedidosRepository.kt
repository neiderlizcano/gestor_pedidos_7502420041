package com.neiderlizcano.gestorpedidos.nativo.data.repository

import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiRequestExecutor
import com.neiderlizcano.gestorpedidos.nativo.core.network.requiredField
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.PedidosApi
import com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper.toPedido
import com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper.toSolicitud
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.*

/** Implementación HTTP de [PedidosRepository] sobre la API REST; usa el ejecutor común con JWT y renovación ante 401. */
class HttpPedidosRepository(private val api: PedidosApi, private val executor: ApiRequestExecutor) : PedidosRepository {
    override suspend fun crear(datos: DatosPedido) = executor.execute({ api.crear(it, datos.toSolicitud()) }) { it.toPedido() }
    override suspend fun listar() = executor.executeRead({ api.listar(it) }) { items -> items.map { requiredField(it).toPedido() } }
    override suspend fun consultar(id: Long) = executor.executeRead({ api.consultar(it, id) }) { it.toPedido() }
    override suspend fun actualizar(id: Long, datos: DatosPedido) = executor.execute({ api.actualizar(it, id, datos.toSolicitud()) }) { it.toPedido() }
    override suspend fun eliminar(id: Long) = executor.executeEmpty { api.eliminar(it, id) }
}
