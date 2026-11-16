package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.di.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.EstadoSesion
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.*
import com.neiderlizcano.gestorpedidos.nativo.ui.reportes.ReporteTipo
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class RuntimeF4 : AuthRuntime, ReportesRuntime {
    val auth = UiRuntime().apply { restoredUser = "A" }
    override val authRepository = auth.authRepository
    override suspend fun inicializar() = auth.inicializar()
    override val reportesRepository = object : ReportesRepository {
        override suspend fun pedidosPorEstadoFechas(filtro: FiltroEstadoFechas, pagina: SolicitudPagina) =
            AppResult.Exito(PaginaReporte<PedidoReporte>(emptyList(), pagina.pagina, pagina.tamano, false))
        override suspend fun pedidosPorUbicacion(filtro: FiltroUbicacion, pagina: SolicitudPagina) =
            AppResult.Exito(PaginaReporte<PedidoReporte>(emptyList(), pagina.pagina, pagina.tamano, false))
        override suspend fun perfilesPorNombreUsername(filtro: FiltroNombreUsername) = AppResult.Exito(emptyList<PerfilReporte>())
        override suspend fun perfilesPorCorreo(correo: String) = AppResult.Exito(listOf(
            PerfilReporte("00000000-0000-0000-0000-000000000001", "cuentaA", "Persona A", correo)))
    }
}

class F4MenuIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun instalar(runtime: RuntimeF4) {
        compose.activityRule.scenario.onActivity { it.setContent { GestorPedidosTheme { AuthRoute(runtime) } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("menuPrincipal").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun reporte(tipo: ReporteTipo) {
        compose.onNodeWithTag("navReportes").performScrollTo()
        compose.onNodeWithTag("navReportes").performClick()
        compose.onNodeWithTag("reporteMenu_" + tipo.name).performScrollTo().performClick()
    }

    @Test fun losCuatroReportesSonAccesiblesDesdeLaSesionYVuelvenAlMenu() {
        instalar(RuntimeF4())
        for (tipo in ReporteTipo.entries) {
            reporte(tipo); compose.onNodeWithTag("reporteLista").assertIsDisplayed()
            compose.onNodeWithTag("navInicio").performClick()
            compose.onNodeWithTag("menuPrincipal").assertIsDisplayed()
        }
    }

    @Test fun sensoresSeAbrenDesdeElMenuPrincipalSinActividadDemo() {
        instalar(RuntimeF4())
        compose.onNodeWithTag("navSensores").performScrollTo()
        compose.onNodeWithTag("navSensores").performClick()
        compose.onNodeWithTag("sensorAcelerometro").performScrollTo()
        compose.onNodeWithTag("sensorAcelerometro").assertExists()
        compose.onNodeWithTag("sensorProximidad").performScrollTo()
        compose.onNodeWithTag("sensorProximidad").assertExists()
        compose.onNodeWithTag("navInicio").performClick()
        compose.onNodeWithTag("menuPrincipal").assertIsDisplayed()
    }

    @Test fun resultadoDeReporteNoPasaALaSiguienteCuentaYElFiltroSobreviveSoloALaRotacion() {
        val runtime = RuntimeF4(); instalar(runtime)
        reporte(ReporteTipo.USUARIOS_CORREO)
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reporteCampo_correo"))
        compose.onNodeWithTag("reporteCampo_correo").performTextInput("cuentaA@example.invalid")
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reporteConsultar"))
        compose.onNodeWithTag("reporteConsultar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reportePerfil_cuentaA").fetchSemanticsNodes().isNotEmpty() }
        // Una ComponentActivity de prueba no conserva su contenido al recrearse: se vuelve a instalar, como en las pruebas de F3.
        // El estado de sesión y el ViewModel del reporte se conservan (el destino sigue siendo Reportes).
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { it.setContent { GestorPedidosTheme { AuthRoute(runtime) } } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("menuPrincipal").fetchSemanticsNodes().isEmpty() }
        // La navegación interna de Reportes puede volver a su menú tras recrear la Activity de prueba; el formulario con el
        // filtro vive en el ViewModel de la sesión y debe conservarse al abrir de nuevo el mismo reporte.
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reporteLista").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("reporteMenu_USUARIOS_CORREO").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithTag("reporteLista").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("reporteMenu_USUARIOS_CORREO").performScrollTo().performClick()
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reporteCampo_correo"))
        compose.onNodeWithTag("reporteCampo_correo").assertTextContains("cuentaA@example.invalid")
        compose.runOnIdle { runtime.auth.session.value = EstadoSesion.SinSesion }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reporteLista").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { runtime.auth.session.value = EstadoSesion.Autenticada("B") }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("menuPrincipal").fetchSemanticsNodes().isNotEmpty() }
        reporte(ReporteTipo.USUARIOS_CORREO)
        compose.onNodeWithTag("reporteLista").performScrollToNode(hasTestTag("reporteCampo_correo"))
        compose.onNodeWithTag("reporteCampo_correo").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        assertEquals(0, compose.onAllNodesWithTag("reportePerfil_cuentaA").fetchSemanticsNodes().size)
    }
}
