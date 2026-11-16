package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.*

/** Puerto adicional del contenedor: reutiliza la sesión y el transporte del CRUD, sin credenciales en la UI. */
interface ReportesRuntime { val reportesRepository: ReportesRepository }

/** La falta de configuración se presenta como error; nunca devuelve datos ficticios de producción. */
object UnavailableReportesRepository : ReportesRepository {
    override suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina) = unavailable<PaginaReporte<PedidoReporte>>()
    override suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina) = unavailable<PaginaReporte<PedidoReporte>>()
    override suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername) = unavailable<List<PerfilReporte>>()
    override suspend fun perfilesPorCorreo(correo: String) = unavailable<List<PerfilReporte>>()
    private fun <T> unavailable(): AppResult<T> = AppResult.Fallo(AppError.ServicioNoDisponible)
}
