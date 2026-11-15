package com.neiderlizcano.gestorpedidos.nativo.domain.reportes

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult

interface ReportesRepository {
    suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina = SolicitudPagina()): AppResult<PaginaReporte<PedidoReporte>>
    suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina = SolicitudPagina()): AppResult<PaginaReporte<PedidoReporte>>
    suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername): AppResult<List<PerfilReporte>>
    suspend fun perfilesPorCorreo(correo: String): AppResult<List<PerfilReporte>>
}
