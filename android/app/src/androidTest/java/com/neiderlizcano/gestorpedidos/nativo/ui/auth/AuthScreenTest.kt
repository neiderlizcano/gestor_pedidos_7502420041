package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.neiderlizcano.gestorpedidos.nativo.di.AuthRuntime
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import com.neiderlizcano.gestorpedidos.nativo.ui.theme.GestorPedidosTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal class UiRuntime : AuthRuntime {
    var initializations = 0
    var logins = 0
    var failLoginOnce = false
    var failFirst = false
    var restoredUser: String? = null
    val session = MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion)
    override val authRepository = object : AuthRepository {
        override val estadoSesion = session
        override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth<Unit> {
            logins++
            if (failLoginOnce && logins == 1) return ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
            session.value = EstadoSesion.Autenticada("test-user"); return ResultadoAuth.Exito(Unit)
        }
        override suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout> {
            session.value = EstadoSesion.SinSesion
            return ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.CONFIRMADA))
        }
        override suspend fun solicitarRecuperacion(email: String): ResultadoAuth<Unit> = ResultadoAuth.Exito(Unit)
        override suspend fun verificarOtp(email: String, codigo: String): ResultadoAuth<RecuperacionId> = ResultadoAuth.Exito(RecuperacionId("ui-test-handle"))
        override suspend fun actualizarPassword(recuperacionId: RecuperacionId, nuevaPassword: String): ResultadoAuth<Unit> = ResultadoAuth.Exito(Unit)
        override suspend fun cancelarRecuperacion() { }
    }
    override suspend fun inicializar(): ResultadoAuth<Unit> {
        initializations++
        session.value = if (failFirst && initializations == 1) EstadoSesion.TemporalmenteNoDisponible else restoredUser?.let { EstadoSesion.Autenticada(it) } ?: EstadoSesion.SinSesion
        return if (session.value == EstadoSesion.TemporalmenteNoDisponible) ResultadoAuth.Fallo(ErrorAuth.CONEXION) else ResultadoAuth.Exito(Unit)
    }
}

class AuthScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun show(runtime: UiRuntime) { compose.setContent { GestorPedidosTheme { AuthRoute(runtime) } } }
    @Test fun recoveryJourneyEndsAtLoginWithoutAuthenticatedHome() {
        show(UiRuntime())
        compose.onNodeWithTag("authRecover").performScrollTo().performClick()
        compose.onNodeWithTag("authEmail").performTextInput("persona@example.invalid")
        compose.onNodeWithTag("authSend").performScrollTo().performClick()
        compose.onNodeWithTag("authCode").performTextInput("test-code")
        compose.onNodeWithTag("authVerify").performScrollTo().performClick()
        compose.onNodeWithTag("authNewPassword").performTextInput("test-password")
        compose.onNodeWithTag("authConfirmation").performTextInput("test-password")
        compose.onNodeWithTag("authUpdate").performScrollTo().performClick()
        compose.onNodeWithTag("authReturnLogin").performScrollTo().performClick()
        compose.onNodeWithTag("authSubmit").assertExists()
        compose.onNodeWithTag("authLogout").assertDoesNotExist()
    }
    @Test fun loginValidationAndPasswordVisibilityAreAccessible() {
        show(UiRuntime())
        compose.onNodeWithTag("authSubmit").performScrollTo().performClick()
        compose.onNodeWithTag("authEmailError", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("authPassword").performTextInput("test-password")
        compose.onNodeWithTag("authPassword").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        val hidden = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("authPassword").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(hidden) }
        assertFalse(hidden.any { it.layoutInput.text.text == "test-password" })
        compose.onNodeWithTag("authPasswordVisibility").performScrollTo().performClick()
        val visible = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("authPassword").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(visible) }
        assertTrue(visible.any { it.layoutInput.text.text == "test-password" })
    }
    @Test fun restorationRetryAndLogoutNeverLeaveAuthenticatedBackDestination() {
        val runtime = UiRuntime().apply { failFirst = true }
        show(runtime)
        compose.onNodeWithTag("authRetry").performClick()
        compose.onNodeWithTag("authEmail").performTextInput("persona@example.invalid")
        compose.onNodeWithTag("authPassword").performTextInput("test-password")
        compose.onNodeWithTag("authSubmit").performScrollTo().performClick()
        compose.onNodeWithTag("authLogout").performClick()
        compose.onNodeWithTag("authSubmit").assertExists()
        compose.onNodeWithTag("authLogout").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, runtime.initializations) }
    }
    @Test fun loginRetryRepeatsLoginAndPreservesFormInputs() {
        val runtime = UiRuntime().apply { failLoginOnce = true }
        show(runtime)
        compose.onNodeWithTag("authEmail").performTextInput("persona@example.invalid")
        compose.onNodeWithTag("authPassword").performTextInput("test-password")
        compose.onNodeWithTag("authSubmit").performScrollTo().performClick()
        compose.onNodeWithTag("authRetry").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("authLogout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("authLogout").assertExists()
        compose.runOnIdle { assertEquals(2, runtime.logins); assertEquals(1, runtime.initializations) }
    }

}
