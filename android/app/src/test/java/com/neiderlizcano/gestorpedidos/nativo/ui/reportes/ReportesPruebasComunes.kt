package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroEstadoFechas
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroNombreUsername
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroUbicacion
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PaginaReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PerfilReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.SolicitudPagina
import kotlinx.coroutines.CompletableDeferred
import java.math.BigDecimal
import java.time.LocalDate

// Datos ficticios; no hay credenciales ni datos personales reales.
internal fun pedidoReporte(id: Long, estado: String = "ENVIADO") = PedidoReporte(
    id, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"),
    "Cliente $id", "Proveedor", BigDecimal("100.50"), estado, "Colombia", "Bolívar", "Cartagena", "Calle ficticia 1", BigDecimal("5.00")
)

internal fun perfilReporte(username: String) = PerfilReporte("00000000-0000-0000-0000-000000000001", username, "Persona $username", "$username@example.invalid")

/** Repositorio simulado que registra las peticiones y pagina en memoria igual que el contrato. */
internal class FakeReportes(
    var pedidos: List<PedidoReporte> = emptyList(),
    var perfiles: List<PerfilReporte> = emptyList()
) : ReportesRepository {
    var error: AppError? = null
    var release: CompletableDeferred<Unit>? = null
    var peticiones = 0
    var ultimoEstadoFechas: Pair<FiltroEstadoFechas, SolicitudPagina>? = null
    var ultimaUbicacion: Pair<FiltroUbicacion, SolicitudPagina>? = null
    var ultimoNombre: FiltroNombreUsername? = null
    var ultimoCorreo: String? = null

    private suspend fun <T> responder(valor: () -> T): AppResult<T> {
        peticiones++
        release?.await()
        error?.let { return AppResult.Fallo(it) }
        return AppResult.Exito(valor())
    }

    private fun pagina(p: SolicitudPagina): PaginaReporte<PedidoReporte> {
        val inicio = p.pagina * p.tamano
        return PaginaReporte(pedidos.drop(inicio).take(p.tamano), p.pagina, p.tamano, inicio + p.tamano < pedidos.size)
    }

    override suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina): AppResult<PaginaReporte<PedidoReporte>> {
        ultimoEstadoFechas = filtro to pagina
        return responder { pagina(pagina) }
    }

    override suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina): AppResult<PaginaReporte<PedidoReporte>> {
        ultimaUbicacion = filtro to pagina
        return responder { pagina(pagina) }
    }

    override suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername): AppResult<List<PerfilReporte>> {
        ultimoNombre = filtro
        return responder { perfiles }
    }

    override suspend fun perfilesPorCorreo(correo: String): AppResult<List<PerfilReporte>> {
        ultimoCorreo = correo
        return responder { perfiles }
    }
}
