package com.neiderlizcano.gestorpedidos.nativo.domain.reportes

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult

/** Cuatro reportes de solo lectura limitados al usuario autenticado: pedidos por estado y fechas, pedidos por ubicación, perfil por nombre o username y perfil por correo. */
interface ReportesRepository {
    suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina = SolicitudPagina()): AppResult<PaginaReporte<PedidoReporte>>
    suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina = SolicitudPagina()): AppResult<PaginaReporte<PedidoReporte>>
    suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername): AppResult<List<PerfilReporte>>
    suspend fun perfilesPorCorreo(correo: String): AppResult<List<PerfilReporte>>
}
