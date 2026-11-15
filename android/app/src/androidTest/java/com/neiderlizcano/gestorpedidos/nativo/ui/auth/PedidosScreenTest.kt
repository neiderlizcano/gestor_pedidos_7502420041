package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.di.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.*
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.*
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

internal fun examplePedido() = Pedido(1, LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-16"), LocalDate.parse("2026-11-17"),
    "Cliente de prueba", "Proveedor", BigDecimal("123.45"), "Pendiente", "Colombia", "Bolívar", "Cartagena", "Calle ficticia", BigDecimal("0.00"))
internal class UiPedidos : PedidosRepository {
    var items = listOf(examplePedido())
    var writes = 0
    var error: AppError? = null
    var readError: AppError? = null
    override suspend fun listar(): AppResult<List<Pedido>> = readError?.let { AppResult.Fallo(it) } ?: AppResult.Exito(items)
    override suspend fun consultar(id: Long): AppResult<Pedido> = AppResult.Exito(items.first { it.id == id })
    private fun write(id: Long, d: DatosPedido): AppResult<Pedido> {
        writes++; error?.let { return AppResult.Fallo(it) }
        val p = Pedido(id, d.fecha, d.fechaEnvio, d.fechaEntrega, d.cliente, d.proveedor, d.valor, d.estado, d.pais, d.departamento, d.ciudad, d.nomenclaturaVivienda, d.propina)
        items = items.filterNot { it.id == id } + p; return AppResult.Exito(p)
    }
    override suspend fun crear(datos: DatosPedido) = write((items.maxOfOrNull { it.id } ?: 0) + 1, datos)
    override suspend fun actualizar(id: Long, datos: DatosPedido) = write(id, datos)
    override suspend fun eliminar(id: Long): AppResult<Unit> { writes++; items = items.filterNot { it.id == id }; return AppResult.Exito(Unit) }
}
internal class PedidosTestRuntime : AuthRuntime, PedidosRuntime {
    val auth = UiRuntime().apply { restoredUser = "A" }
    override val authRepository = auth.authRepository
    override suspend fun inicializar() = auth.inicializar()
    override var pedidosRepository: PedidosRepository = UiPedidos()
}
/** Tras iniciar sesión se muestra el menú central: abre «Pedidos» si todavía está visible (tras recrear la Activity ya puede estar abierto). */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.abrirPedidos() {
    if (onAllNodesWithTag("navPedidos").fetchSemanticsNodes().isNotEmpty()) { onNodeWithTag("navPedidos").performClick(); waitForIdle() }
}
class PedidosScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun install(runtime: PedidosTestRuntime) {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { GestorPedidosTheme { AuthRoute(runtime) } } }
        compose.waitForIdle(); compose.abrirPedidos()
    }
    private fun show(tag: String) {
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasTestTag(tag))
    }
    private fun click(tag: String) { show(tag); compose.onNodeWithTag(tag).performClick() }
    private fun enter(field: PedidoField, value: String) {
        val tag = "pedidoField_" + field.name
        show(tag)
        if (field in setOf(PedidoField.FECHA, PedidoField.FECHA_ENVIO, PedidoField.FECHA_ENTREGA)) {
            compose.onNodeWithTag(tag).performClick(); selectPedidoDate(compose, compose.activity, value)
        } else if (field == PedidoField.ESTADO) {
            // Lista cerrada: se abre el selector y se elige la opción (no se escribe texto).
            compose.onNodeWithTag(tag).performClick()
            compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("pedidoEstadoMenu")), useUnmergedTree = true).performClick()
        } else compose.onNodeWithTag(tag).performTextReplacement(value)
    }
    @Test fun crudJourneyDisplaysThirteenAttributesAndConfirmation() {
        val runtime = PedidosTestRuntime(); install(runtime)
        click("pedidoNuevo")
        for ((field,value) in PedidoDraft.from(examplePedido()).values) enter(field,value)
        click("pedidoGuardar")
        show("pedidoId"); compose.onNodeWithTag("pedidoId").assertTextContains("2", substring = true)
        click("pedidoEditar"); enter(PedidoField.CLIENTE, "Cliente editado"); click("pedidoGuardar")
        compose.onNodeWithTag("pedidosScreen").performScrollToNode(hasText("Cliente editado"))
        compose.onNodeWithText("Cliente editado").assertExists()
        click("pedidoEliminar"); compose.onNodeWithTag("pedidoConfirmarEliminar").performClick()
        compose.onNodeWithTag("pedidoRow_2").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, (runtime.pedidosRepository as UiPedidos).writes) }
    }
    @Test fun editableDraftSurvivesActivityRecreationButNotAccountSwitch() {
        val runtime = PedidosTestRuntime(); install(runtime)
        click("pedidoNuevo"); enter(PedidoField.CLIENTE, "Borrador privado A")
        compose.activityRule.scenario.recreate(); install(runtime)
        show("pedidoField_CLIENTE"); compose.onNodeWithTag("pedidoField_CLIENTE").assertTextContains("Borrador privado A")
        compose.runOnIdle {
            runtime.pedidosRepository = UiPedidos().apply { items = emptyList() }
            runtime.auth.session.value = EstadoSesion.Autenticada("B")
        }
        compose.waitForIdle(); compose.abrirPedidos(); click("pedidoNuevo"); show("pedidoField_CLIENTE")
        compose.onNodeWithTag("pedidoField_CLIENTE").assertTextContains("")
        compose.onNodeWithTag("pedidoField_CLIENTE").assert(SemanticsMatcher("Sin borrador A") { node ->
            node.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text.isEmpty()
        })
        compose.runOnIdle { assertEquals(1, runtime.auth.initializations) }
    }
    @Test fun invalidSessionOnWriteKeepsEditorAndDoesNotLogout() {
        val runtime = PedidosTestRuntime(); (runtime.pedidosRepository as UiPedidos).error = AppError.SesionNoValida
        install(runtime); click("pedidoNuevo")
        for ((field,value) in PedidoDraft.from(examplePedido()).values) enter(field,value)
        click("pedidoGuardar"); show("pedidosError"); compose.onNodeWithTag("pedidosError").assertExists()
        show("pedidoField_CLIENTE"); compose.onNodeWithTag("pedidoField_CLIENTE").assertTextContains("Cliente de prueba")
        // La sesión sigue activa: el menú ofrece Cerrar sesión y el borrador se conserva al volver.
        compose.onNodeWithTag("navInicio").performClick(); compose.onNodeWithTag("authLogout").assertExists(); compose.onNodeWithTag("navPedidos").performClick()
        show("pedidoField_CLIENTE"); compose.onNodeWithTag("pedidoField_CLIENTE").assertTextContains("Cliente de prueba")
        compose.runOnIdle { assertEquals(1, (runtime.pedidosRepository as UiPedidos).writes) }
    }
    @Test fun contractErrorsAreShownBesideTheirFieldsWithoutSendingRequest() {
        val runtime = PedidosTestRuntime(); install(runtime); click("pedidoNuevo")
        for ((field,value) in PedidoDraft.from(examplePedido()).values) enter(field,value)
        enter(PedidoField.VALOR, "0"); enter(PedidoField.PROPINA, "10000000000.00")
        enter(PedidoField.FECHA_ENVIO, "2026-11-14"); enter(PedidoField.FECHA_ENTREGA, "2026-11-13")
        click("pedidoGuardar")
        val errors = mapOf(PedidoField.VALOR to "El valor debe ser mayor que cero.",
            PedidoField.PROPINA to "El importe admite como máximo 10 dígitos enteros.",
            PedidoField.FECHA_ENVIO to "La fecha de envío no puede ser anterior a la fecha del pedido.",
            PedidoField.FECHA_ENTREGA to "La fecha de entrega no puede ser anterior a la fecha de envío.")
        for ((field,message) in errors) {
            show("pedidoField_" + field.name)
            compose.onNodeWithTag("pedidoError_" + field.name, useUnmergedTree = true).assertTextEquals(message)
        }
        enter(PedidoField.VALOR, "0.001"); click("pedidoGuardar"); show("pedidoField_VALOR")
        compose.onNodeWithTag("pedidoError_VALOR", useUnmergedTree = true).assertTextEquals("El importe admite como máximo 2 decimales, sin redondear.")
        compose.runOnIdle { assertEquals(0, (runtime.pedidosRepository as UiPedidos).writes) }
    }
    @Test fun uncertainWriteDisablesSaveUntilSuccessfulListCheckEvenAfterRecreation() {
        val runtime = PedidosTestRuntime(); val repo = runtime.pedidosRepository as UiPedidos
        repo.error = AppError.ErrorConexion
        install(runtime); click("pedidoNuevo")
        for ((field,value) in PedidoDraft.from(examplePedido()).values) enter(field,value)
        click("pedidoGuardar"); compose.onNodeWithTag("pedidoGuardar").assertIsNotEnabled()
        compose.activityRule.scenario.recreate(); install(runtime)
        show("pedidoGuardar"); compose.onNodeWithTag("pedidoGuardar").assertIsNotEnabled()
        compose.runOnIdle { repo.readError = AppError.ErrorConexion }
        click("pedidosComprobar"); show("pedidoGuardar"); compose.onNodeWithTag("pedidoGuardar").assertIsNotEnabled()
        compose.runOnIdle { repo.readError = null; repo.error = null; assertEquals(1, repo.writes) }
        click("pedidosComprobar"); compose.onNodeWithText("Continuar borrador").performClick()
        show("pedidoGuardar"); compose.onNodeWithTag("pedidoGuardar").assertIsEnabled()
        show("pedidoField_CLIENTE"); compose.onNodeWithTag("pedidoField_CLIENTE").assertTextContains("Cliente de prueba")
        compose.runOnIdle { assertEquals(1, repo.writes) }
    }

}
