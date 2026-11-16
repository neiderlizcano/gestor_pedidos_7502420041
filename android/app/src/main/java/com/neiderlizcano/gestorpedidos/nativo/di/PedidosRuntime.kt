package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.*
import com.neiderlizcano.gestorpedidos.nativo.core.result.*

/** Puerto que expone el repositorio de pedidos del contenedor de servicios. */
interface PedidosRuntime { val pedidosRepository: PedidosRepository }
/** Repositorio sin configuración: toda operación devuelve [AppError.ServicioNoDisponible]; nunca datos simulados. */
object UnavailablePedidosRepository : PedidosRepository {
    override suspend fun crear(datos: DatosPedido) = unavailable<Pedido>()
    override suspend fun listar() = unavailable<List<Pedido>>()
    override suspend fun consultar(id: Long) = unavailable<Pedido>()
    override suspend fun actualizar(id: Long, datos: DatosPedido) = unavailable<Pedido>()
    override suspend fun eliminar(id: Long) = unavailable<Unit>()
    private fun <T> unavailable(): AppResult<T> = AppResult.Fallo(AppError.ServicioNoDisponible)
}
