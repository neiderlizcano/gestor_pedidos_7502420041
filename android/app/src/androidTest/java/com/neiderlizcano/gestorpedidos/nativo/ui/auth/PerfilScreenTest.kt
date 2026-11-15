package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.di.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.*
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class UiPerfiles : UsuariosRepository {
    var perfil: Perfil? = Perfil("A", "personaA", "Persona A", "a@example.invalid")
    var readError: AppError? = null
    var writeError: AppError? = null
    var creates = 0; var updates = 0; var deletes = 0
    override suspend fun consultarPerfilPropio(): AppResult<Perfil> = readError?.let { AppResult.Fallo(it) }
        ?: perfil?.let { AppResult.Exito(it) } ?: AppResult.Fallo(AppError.ErrorHttp(404))
    private fun save(d: DatosPerfil): AppResult<Perfil> {
        writeError?.let { return AppResult.Fallo(it) }
        perfil = Perfil("A", d.username, d.nombre, "a@example.invalid"); return AppResult.Exito(perfil!!)
    }
    override suspend fun crearPerfil(datos: DatosPerfil): AppResult<Perfil> { creates++; return save(datos) }
    override suspend fun actualizarPerfilPropio(datos: DatosPerfil): AppResult<Perfil> { updates++; return save(datos) }
    override suspend fun eliminarCuentaPropia(): AppResult<Unit> {
        deletes++; writeError?.let { return AppResult.Fallo(it) }; perfil = null; return AppResult.Exito(Unit)
    }
}
internal class PerfilRuntime : AuthRuntime, PedidosRuntime, UsuariosRuntime {
    val auth = UiRuntime().apply { restoredUser = "A" }
    var logouts = 0
    override val authRepository = object : AuthRepository by auth.authRepository {
        override suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout> { logouts++; return auth.authRepository.cerrarSesion() }
    }
    override suspend fun inicializar() = auth.inicializar()
    override val pedidosRepository = UiPedidos()
    override var usuariosRepository: UsuariosRepository = UiPerfiles()
}
class PerfilScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun install(runtime: PerfilRuntime) {
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { GestorPedidosTheme { AuthRoute(runtime) } } }
        compose.waitForIdle()
    }
    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (tag.startsWith("nav") || tag == "authLogout") node.performClick() else node.performScrollTo().performClick()
    }
    private fun fill() {
        compose.onNodeWithTag("perfilUsername").performScrollTo().performTextReplacement("nuevo")
        compose.onNodeWithTag("perfilNombre").performScrollTo().performTextReplacement("Nombre nuevo")
    }
    @Test fun ownProfileCrudRequiresExplicitCreateAndConfirmsAccountAndOrdersDeletion() {
        val runtime = PerfilRuntime(); val repo = runtime.usuariosRepository as UiPerfiles; repo.perfil = null
        install(runtime); click("navPerfil")
        compose.onNodeWithTag("perfilMissing").assertExists(); compose.runOnIdle { assertEquals(0, repo.creates) }
        click("perfilCrear"); click("perfilGuardar")
        compose.onNodeWithTag("perfilUsername").assertExists(); compose.runOnIdle { assertEquals(0, repo.creates) }
        fill(); click("perfilGuardar"); compose.onNodeWithTag("perfilNombreValue").assertTextEquals("Nombre nuevo")
        click("perfilEditar"); compose.onNodeWithTag("perfilCorreo").assertTextEquals("a@example.invalid")
        compose.onNodeWithTag("perfilCorreo").assert(!SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
        compose.onNodeWithTag("perfilNombre").performTextReplacement("Nombre editado"); click("perfilGuardar")
        click("perfilEliminar")
        compose.onNodeWithText("Se eliminará tu cuenta de acceso, tu perfil y todos tus pedidos. Esta acción no se puede deshacer. ¿Quieres continuar?").assertExists()
        compose.onNodeWithTag("perfilCancelarEliminar").performClick(); compose.runOnIdle { assertEquals(0, repo.deletes) }
        click("perfilEliminar"); compose.onNodeWithTag("perfilConfirmarEliminar").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("authSubmit").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("perfilScreen").assertDoesNotExist(); compose.onNodeWithTag("navPerfil").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, repo.creates); assertEquals(1, repo.updates); assertEquals(1, repo.deletes); assertEquals(1, runtime.logouts) }
    }
    @Test fun draftAndDestinationSurviveRotationButDisappearAfterAccountChangeOrLogout() {
        val runtime = PerfilRuntime(); install(runtime); click("navPerfil"); click("perfilEditar"); fill()
        click("navPedidos"); click("navPerfil"); compose.onNodeWithTag("perfilNombre").assertTextContains("Nombre nuevo")
        compose.activityRule.scenario.recreate(); install(runtime)
        compose.onNodeWithTag("perfilNombre").assertTextContains("Nombre nuevo")
        compose.runOnIdle {
            runtime.usuariosRepository = UiPerfiles().apply { perfil = Perfil("B", "personaB", "Persona B", "b@example.invalid") }
            runtime.auth.session.value = EstadoSesion.Autenticada("B")
        }
        click("navPerfil"); compose.onNodeWithTag("perfilNombreValue").assertTextEquals("Persona B")
        click("perfilEditar"); compose.onNodeWithTag("perfilNombre").assertTextContains("Persona B")
        click("authLogout")
        compose.waitUntil(5000) { compose.onAllNodesWithTag("authSubmit").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("perfilScreen").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, runtime.auth.initializations) }
    }
    @Test fun uncertainCreationKeepsDraftAndCannotRepeatUntilSuccessfulQuery() {
        val runtime = PerfilRuntime(); val repo = runtime.usuariosRepository as UiPerfiles
        repo.perfil = null; repo.writeError = AppError.RespuestaInvalida
        install(runtime); click("navPerfil"); click("perfilCrear"); fill(); click("perfilGuardar")
        compose.onNodeWithTag("perfilGuardar").assertIsNotEnabled()
        compose.runOnIdle { repo.readError = AppError.ServicioNoDisponible }
        click("perfilConsultar"); compose.onNodeWithTag("perfilGuardar").assertIsNotEnabled()
        compose.onNodeWithTag("perfilNombre").assertTextContains("Nombre nuevo")
        compose.runOnIdle {
            repo.readError = null; repo.writeError = null
            repo.perfil = Perfil("A", "nuevo", "Nombre nuevo", "a@example.invalid")
            assertEquals(1, repo.creates)
        }
        click("perfilConsultar"); click("perfilGuardar")
        compose.runOnIdle { assertEquals(1, repo.creates); assertEquals(1, repo.updates); assertEquals(0, runtime.logouts) }
    }
    @Test fun uncertainDeletionAndProfile404NeverPretendAccountWasDeleted() {
        val runtime = PerfilRuntime(); val repo = runtime.usuariosRepository as UiPerfiles
        install(runtime); click("navPerfil"); repo.writeError = AppError.ErrorConexion
        click("perfilEliminar"); compose.onNodeWithTag("perfilConfirmarEliminar").performClick()
        compose.onNodeWithTag("perfilUncertain").assertExists(); compose.onNodeWithTag("perfilEliminar").assertIsNotEnabled()
        compose.runOnIdle { repo.perfil = null }
        click("perfilConsultar")
        compose.onNodeWithTag("perfilCrear").assertIsNotEnabled()
        compose.onNodeWithTag("perfilEliminar").assertIsNotEnabled(); compose.onNodeWithTag("authLogout").assertExists()
        compose.runOnIdle { assertEquals(1, repo.deletes); assertEquals(0, runtime.logouts) }
    }
    @Test fun expiredSessionWriteShowsErrorAndPreservesInputsWithoutLogout() {
        val runtime = PerfilRuntime(); val repo = runtime.usuariosRepository as UiPerfiles
        install(runtime); click("navPerfil"); click("perfilEditar"); fill()
        compose.runOnIdle { repo.writeError = AppError.SesionNoValida }
        click("perfilGuardar"); compose.onNodeWithTag("perfilError").assertExists()
        compose.onNodeWithTag("perfilNombre").assertTextContains("Nombre nuevo"); compose.onNodeWithTag("authLogout").assertExists()
        compose.runOnIdle { assertEquals(1, repo.updates); assertEquals(0, runtime.logouts) }
    }
}
