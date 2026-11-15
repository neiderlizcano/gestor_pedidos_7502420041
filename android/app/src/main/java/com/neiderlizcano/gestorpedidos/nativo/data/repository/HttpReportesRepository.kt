package com.neiderlizcano.gestorpedidos.nativo.data.repository

import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiRequestExecutor
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.ReportesApi
import com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper.toPagina
import com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper.toReportePerfiles
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.*

class HttpReportesRepository(private val api: ReportesApi, private val executor: ApiRequestExecutor) : ReportesRepository {
    override suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina) =
        executor.executeRead({ authorization ->
            api.estadoFechas(authorization, filtro.estado, filtro.desde?.toString(), filtro.hasta?.toString(),
                when (filtro.fechaBase) {
                    FechaBase.FECHA -> "fecha"
                    FechaBase.FECHA_ENVIO -> "fecha_envio"
                    FechaBase.FECHA_ENTREGA -> "fecha_entrega"
                }, pagina.pagina, pagina.tamano)
        }) { it.toPagina() }

    override suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina) =
        executor.executeRead({ api.ubicacion(it, filtro.pais, filtro.departamento, filtro.ciudad, pagina.pagina, pagina.tamano) }) { it.toPagina() }

    override suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername) = executor.executeRead({ authorization ->
        api.nombreUsername(authorization,
            when (filtro.campo) { CampoPerfil.NOMBRE -> "nombre"; CampoPerfil.USERNAME -> "username" },
            filtro.termino,
            when (filtro.coincidencia) { CoincidenciaPerfil.EXACTA -> "exacta"; CoincidenciaPerfil.PREFIJO -> "prefijo" })
    }) { it.toReportePerfiles() }

    override suspend fun perfilesPorCorreo(correo: String) = executor.executeRead({ api.correo(it, correo) }) { it.toReportePerfiles() }
}
