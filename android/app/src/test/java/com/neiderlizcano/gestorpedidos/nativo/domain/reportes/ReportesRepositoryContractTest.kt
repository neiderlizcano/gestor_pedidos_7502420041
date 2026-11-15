package com.neiderlizcano.gestorpedidos.nativo.domain.reportes

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Small fake usable by future report ViewModels without transport, Auth, or Android. */
private class ReportesSimulados : ReportesRepository {
    var paginaRecibida: SolicitudPagina? = null
    var filtroRecibido: FiltroEstadoFechas? = null
    var respuesta: AppResult<PaginaReporte<PedidoReporte>> = AppResult.Fallo(AppError.ServicioNoDisponible)
    override suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina): AppResult<PaginaReporte<PedidoReporte>> {
        filtroRecibido = filtro
        paginaRecibida = pagina
        return respuesta
    }
    override suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina): AppResult<PaginaReporte<PedidoReporte>> = respuesta
    override suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername): AppResult<List<PerfilReporte>> = AppResult.Exito(emptyList())
    override suspend fun perfilesPorCorreo(correo: String): AppResult<List<PerfilReporte>> = AppResult.Exito(emptyList())
}

class ReportesRepositoryContractTest {
    @Test fun fakeIsSubstitutableAndRepresentsUnavailableThenEmptyPage() = runBlocking {
        val fake = ReportesSimulados()
        val repository: ReportesRepository = fake
        val filtro = FiltroEstadoFechas(estado = "Pendiente")
        assertEquals(AppResult.Fallo(AppError.ServicioNoDisponible), repository.pedidosPorEstadoFechas(filtro))
        assertEquals(SolicitudPagina(0, 20), fake.paginaRecibida)
        assertEquals(filtro, fake.filtroRecibido)
        fake.respuesta = AppResult.Exito(PaginaReporte(emptyList(), 0, 20, false))
        val recovered = repository.pedidosPorEstadoFechas(filtro)
        assertEquals(fake.respuesta, recovered)
        assertFalse((recovered as AppResult.Exito).valor.haySiguiente)
    }
}
