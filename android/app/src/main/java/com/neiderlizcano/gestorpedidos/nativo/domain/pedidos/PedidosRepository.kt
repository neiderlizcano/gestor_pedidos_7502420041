package com.neiderlizcano.gestorpedidos.nativo.domain.pedidos

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult

interface PedidosRepository {
    suspend fun crear(datos: DatosPedido): AppResult<Pedido>
    suspend fun listar(): AppResult<List<Pedido>>
    suspend fun consultar(id: Long): AppResult<Pedido>
    suspend fun actualizar(id: Long, datos: DatosPedido): AppResult<Pedido>
    suspend fun eliminar(id: Long): AppResult<Unit>
}
