package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.neiderlizcano.gestorpedidos.nativo.R
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
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import com.neiderlizcano.gestorpedidos.nativo.ui.auth.selectPedidoDate
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

// Repositorio simulado de la prueba instrumentada: datos ficticios y paginación en memoria.
private class RepositorioReportesUi(
    var pedidos: List<PedidoReporte> = emptyList(),
    var perfiles: List<PerfilReporte> = emptyList()
) : ReportesRepository {
    var error: AppError? = null
    var peticiones = 0
    var ultimaPagina: SolicitudPagina? = null
    var ultimoFiltro: FiltroEstadoFechas? = null

    private fun <T> responder(valor: () -> T): AppResult<T> {
        peticiones++
        error?.let { return AppResult.Fallo(it) }
        return AppResult.Exito(valor())
    }

    private fun pagina(p: SolicitudPagina): PaginaReporte<PedidoReporte> {
        ultimaPagina = p
        val inicio = p.pagina * p.tamano
        return PaginaReporte(pedidos.drop(inicio).take(p.tamano), p.pagina, p.tamano, inicio + p.tamano < pedidos.size)
    }

    override suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina) = responder { ultimoFiltro = filtro; pagina(pagina) }
    override suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina) = responder { pagina(pagina) }
    override suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername) = responder { perfiles }
    override suspend fun perfilesPorCorreo(correo: String) = responder { perfiles }
}

private fun pedido(id: Long) = PedidoReporte(
    id, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03"),
    "Cliente $id", "Proveedor", BigDecimal("100.50"), "ENVIADO", "Colombia", "Bolívar", "Cartagena", "Calle ficticia 1", BigDecimal("5.00")
)

private fun perfil(username: String) = PerfilReporte("00000000-0000-0000-0000-000000000001", username, "Persona $username", "$username@example.invalid")

class ReportesScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun texto(id: Int) = contexto.getString(id)

    private fun mostrar(repo: ReportesRepository) {
        compose.activityRule.scenario.onActivity { it.setContent { GestorPedidosTheme { ReportesRoute(repo) } } }
        compose.waitForIdle()
    }

    private fun esperar(tag: String) = compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun visible(tag: String) { compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag(tag)) }
    private fun pulsar(tag: String) { visible(tag); compose.onNodeWithTag(tag).performClick() }
    private fun escribir(tag: String, valor: String) { visible(tag); compose.onNodeWithTag(tag).performTextInput(valor) }
    private fun menu(tipo: ReporteTipo) = compose.onNodeWithTag("reporteMenu_" + tipo.name).performClick()

    @Test fun menuOfreceLosCuatroReportesYPermiteVolver() {
        mostrar(RepositorioReportesUi())
        ReporteTipo.entries.forEach { compose.onNodeWithTag("reporteMenu_" + it.name).assertExists() }
        menu(ReporteTipo.PEDIDOS_UBICACION)
        compose.onNodeWithTag("reporteConsultar").assertExists()
        compose.onNodeWithTag("reportesVolver").performClick()
        compose.onNodeWithTag("reporteMenu_PEDIDOS_ESTADO_FECHAS").assertExists()
    }

    @Test fun pedidosPorEstadoValidaFechasYPagina() {
        val repo = RepositorioReportesUi(pedidos = (1L..25L).map(::pedido))
        mostrar(repo); menu(ReporteTipo.PEDIDOS_ESTADO_FECHAS)
        compose.onNodeWithTag("reporteInicial").assertExists()
        // Las fechas se eligen en el calendario: desde posterior a hasta es un rango inválido y no envía la petición.
        pulsar("reporteCampo_desde"); selectPedidoDate(compose, compose.activity, "2026-03-10")
        pulsar("reporteCampo_hasta"); selectPedidoDate(compose, compose.activity, "2026-03-01")
        pulsar("reporteConsultar")
        compose.onNodeWithText(texto(R.string.reportes_problem_rango)).assertExists()
        assertEquals(0, repo.peticiones)
        // Cada fecha se puede quitar; Limpiar descarta todo. Después se elige un estado de la lista y una fecha desde.
        pulsar("reporteCampo_hastaQuitar")
        pulsar("reporteLimpiar")
        pulsar("reporteCampo_estado")
        compose.onNode(hasText("Enviado") and hasAnyAncestor(hasTestTag("reporteEstadoMenu")), useUnmergedTree = true).performClick()
        pulsar("reporteCampo_desde"); selectPedidoDate(compose, compose.activity, "2026-03-10")
        pulsar("reporteTamano_10"); pulsar("reporteConsultar")
        // Las filas quedan bajo el área visible de la lista perezosa: se espera la respuesta y se desplaza hasta la primera.
        compose.waitUntil(5_000) { repo.peticiones == 1 }
        compose.waitForIdle()
        visible("reportePedido_1")
        esperar("reportePedido_1")
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reportePagina"))
        compose.onNodeWithTag("reporteAnterior").assertIsNotEnabled()
        pulsar("reporteSiguiente")
        // La lista conserva su desplazamiento: se espera la página 2 y se baja/sube hasta la fila 11.
        compose.waitUntil(5_000) { compose.onAllNodesWithText(contexto.getString(R.string.reportes_page, 2)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reportePedido_11"))
        compose.onNodeWithTag("reportePedido_11").assertExists()
        assertEquals(1, repo.ultimaPagina!!.pagina)
        assertEquals("Enviado", repo.ultimoFiltro!!.estado)
        assertEquals(LocalDate.parse("2026-03-10"), repo.ultimoFiltro!!.desde)
        assertEquals(null, repo.ultimoFiltro!!.hasta)
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reportePagina"))
        compose.onNodeWithTag("reporteAnterior").assertIsEnabled()
    }

    @Test fun pedidosPorUbicacionExigeUnFiltroYMuestraEstadoVacio() {
        val repo = RepositorioReportesUi()
        mostrar(repo); menu(ReporteTipo.PEDIDOS_UBICACION)
        pulsar("reporteConsultar")
        compose.onNodeWithTag("reporteProblema_ubicacion").assertExists()
        assertEquals(0, repo.peticiones)
        escribir("reporteCampo_pais", "Colombia"); pulsar("reporteConsultar")
        esperar("reporteVacio")
        assertEquals(1, repo.peticiones)
    }

    @Test fun perfilesPorNombreMuestraResultadosYValidaTermino() {
        val repo = RepositorioReportesUi(perfiles = listOf(perfil("ana")))
        mostrar(repo); menu(ReporteTipo.USUARIOS_NOMBRE_USERNAME)
        pulsar("reporteConsultar")
        compose.onNodeWithText(texto(R.string.reportes_problem_obligatorio)).assertExists()
        assertEquals(0, repo.peticiones)
        // Ya no existe el selector Nombre/Username: la búsqueda es solo por nombre.
        compose.onAllNodesWithTag("reporteCampoPerfil_USERNAME").assertCountEquals(0)
        escribir("reporteCampo_termino", "ana"); pulsar("reporteConsultar")
        esperar("reportePerfil_ana")
    }

    @Test fun perfilesPorCorreoMuestraErrorYPermiteReintentar() {
        val repo = RepositorioReportesUi(perfiles = listOf(perfil("ana")))
        mostrar(repo); menu(ReporteTipo.USUARIOS_CORREO)
        escribir("reporteCampo_correo", "no-es-correo"); pulsar("reporteConsultar")
        compose.onNodeWithText(texto(R.string.reportes_problem_correo)).assertExists()
        pulsar("reporteLimpiar")
        escribir("reporteCampo_correo", "ana@example.invalid")
        repo.error = AppError.ErrorConexion
        pulsar("reporteConsultar"); esperar("reporteError")
        compose.onNodeWithText(texto(R.string.reportes_error_connection)).assertExists()
        repo.error = null
        pulsar("reporteReintentar"); esperar("reportePerfil_ana")
        assertEquals(2, repo.peticiones)
    }

    @Test fun elFormularioYElReporteElegidoSobrevivenALaRecreacionDeLaActividad() {
        val repo = RepositorioReportesUi()
        mostrar(repo); menu(ReporteTipo.USUARIOS_CORREO)
        escribir("reporteCampo_correo", "ana@example.invalid")
        compose.activityRule.scenario.recreate()
        mostrar(repo)
        compose.onNodeWithTag("reporteCampo_correo").assertExists()
        compose.onNodeWithText("ana@example.invalid").assertExists()
    }
}
