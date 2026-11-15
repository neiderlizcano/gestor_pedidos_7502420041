package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class FakeUiAuth : AuthRepository {
    override val estadoSesion = MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion)
    var logins = 0
    var requests = 0
    var requestResult: ResultadoAuth<Unit> = ResultadoAuth.Exito(Unit)
    var loginCall: suspend () -> ResultadoAuth<Unit> = { estadoSesion.value = EstadoSesion.Autenticada("test-user"); ResultadoAuth.Exito(Unit) }
    var verifyResult: ResultadoAuth<RecuperacionId> = ResultadoAuth.Exito(RecuperacionId("test-handle"))
    var updateResult: ResultadoAuth<Unit> = ResultadoAuth.Exito(Unit)
    var logoutResult = ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.CONFIRMADA))
    override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth<Unit> { logins++; return loginCall() }
    override suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout> { estadoSesion.value = EstadoSesion.SinSesion; return logoutResult }
    override suspend fun solicitarRecuperacion(email: String): ResultadoAuth<Unit> { requests++; return requestResult }
    override suspend fun verificarOtp(email: String, codigo: String) = verifyResult
    override suspend fun actualizarPassword(recuperacionId: RecuperacionId, nuevaPassword: String) = updateResult
    override suspend fun cancelarRecuperacion() { }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test fun initializesAtStartupAndExplicitRetry() = runTest {
        val repo = FakeUiAuth(); var calls = 0
        val vm = AuthViewModel(repo, { calls++; ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.reintentar(); runCurrent()
        assertEquals(2, calls)
        assertEquals(AuthDestination.FORMULARIO, vm.uiState.value.destination)
    }
    @Test fun invalidLoginNeverCallsRepository() = runTest {
        val repo = FakeUiAuth(); val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.login("invalid", "test-password")
        assertEquals(AuthValidation.EMAIL, vm.uiState.value.validation)
        vm.login("persona@example.invalid", "")
        assertEquals(AuthValidation.PASSWORD, vm.uiState.value.validation)
        assertEquals(0, repo.logins)
    }
    @Test fun loginLoadingPreventsDuplicateSubmissionAndRoutesToHome() = runTest {
        val repo = FakeUiAuth(); val release = CompletableDeferred<Unit>()
        repo.loginCall = { release.await(); repo.estadoSesion.value = EstadoSesion.Autenticada("test-user"); ResultadoAuth.Exito(Unit) }
        val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.login("persona@example.invalid", "test-password"); runCurrent()
        assertTrue(vm.uiState.value.busy)
        vm.login("persona@example.invalid", "test-password"); runCurrent()
        assertEquals(1, repo.logins)
        release.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.busy)
        assertEquals(AuthDestination.INICIO, vm.uiState.value.destination)
    }
    @Test fun loginErrorIsVisibleWithoutAuthenticatedNavigation() = runTest {
        val repo = FakeUiAuth(); repo.loginCall = { ResultadoAuth.Fallo(ErrorAuth.CREDENCIALES_RECHAZADAS) }
        val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.login("persona@example.invalid", "test-password"); runCurrent()
        assertEquals(ErrorAuth.CREDENCIALES_RECHAZADAS, vm.uiState.value.error)
        assertEquals(AuthDestination.FORMULARIO, vm.uiState.value.destination)
    }
    @Test fun fullRecoveryRequiresLoginInsteadOfHome() = runTest {
        val vm = AuthViewModel(FakeUiAuth(), { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.recuperar(); vm.solicitar("persona@example.invalid"); runCurrent()
        assertEquals(AuthStep.OTP, vm.uiState.value.step)
        vm.verificar("test-code"); runCurrent()
        assertEquals(AuthStep.NUEVA_PASSWORD, vm.uiState.value.step)
        vm.actualizar("test-password", "different")
        assertEquals(AuthValidation.CONFIRMACION, vm.uiState.value.validation)
        vm.actualizar("test-password", "test-password"); runCurrent()
        assertEquals(AuthStep.COMPLETADA, vm.uiState.value.step)
        assertEquals(AuthDestination.FORMULARIO, vm.uiState.value.destination)
        vm.volverLogin(); runCurrent()
        assertEquals(AuthStep.LOGIN, vm.uiState.value.step)
    }
    @Test fun invalidOtpDoesNotAdvanceAndCanBeRetried() = runTest {
        val repo = FakeUiAuth(); val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.recuperar(); vm.solicitar("persona@example.invalid"); runCurrent()
        repo.verifyResult = ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO)
        vm.verificar("bad-code"); runCurrent()
        assertEquals(AuthStep.OTP, vm.uiState.value.step)
        assertEquals(ErrorAuth.OTP_INVALIDO_O_EXPIRADO, vm.uiState.value.error)
        repo.verifyResult = ResultadoAuth.Exito(RecuperacionId("next"))
        vm.verificar("test-code"); runCurrent()
        assertEquals(AuthStep.NUEVA_PASSWORD, vm.uiState.value.step)
    }
    @Test fun uncertainUpdateReturnsToEmailWithoutAutomaticResubmission() = runTest {
        val repo = FakeUiAuth(); val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.recuperar(); vm.solicitar("persona@example.invalid"); runCurrent(); vm.verificar("test-code"); runCurrent()
        repo.updateResult = ResultadoAuth.Fallo(ErrorAuth.CONEXION)
        vm.actualizar("test-password", "test-password"); runCurrent()
        assertEquals(AuthStep.CORREO, vm.uiState.value.step)
        assertEquals(ErrorAuth.CONEXION, vm.uiState.value.error)
        assertEquals(1, repo.requests)
    }
    @Test fun restorationRetryAndLogoutSelectDestinationsFromSession() = runTest {
        val repo = FakeUiAuth(); repo.estadoSesion.value = EstadoSesion.TemporalmenteNoDisponible
        var calls = 0
        val vm = AuthViewModel(repo, {
            calls++
            if (calls == 1) ResultadoAuth.Fallo(ErrorAuth.CONEXION)
            else { repo.estadoSesion.value = EstadoSesion.Autenticada("test-user"); ResultadoAuth.Exito(Unit) }
        }, backgroundScope)
        runCurrent(); assertEquals(AuthDestination.ERROR_RESTAURACION, vm.uiState.value.destination)
        vm.reintentar(); runCurrent(); assertEquals(AuthDestination.INICIO, vm.uiState.value.destination)
        vm.logout(); runCurrent(); assertEquals(AuthDestination.FORMULARIO, vm.uiState.value.destination)
        assertEquals(AuthStep.LOGIN, vm.uiState.value.step)
    }
    @Test fun cancellationReturnsRecoveryToLogin() = runTest {
        val vm = AuthViewModel(FakeUiAuth(), { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.recuperar(); vm.volverLogin(); runCurrent()
        assertEquals(AuthStep.LOGIN, vm.uiState.value.step)
    }
    @Test fun formFailuresKeepTheirOwnRetryStepWithoutReinitializing() = runTest {
        val repo = FakeUiAuth(); var initializations = 0
        val vm = AuthViewModel(repo, { initializations++; ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent()
        repo.loginCall = { ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE) }
        vm.login("persona@example.invalid", "test-password"); runCurrent()
        assertEquals(AuthStep.LOGIN, vm.uiState.value.retryStep)
        vm.login("persona@example.invalid", "test-password"); runCurrent()
        assertEquals(2, repo.logins)
        vm.recuperar(); repo.requestResult = ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
        vm.solicitar("persona@example.invalid"); runCurrent()
        assertEquals(AuthStep.CORREO, vm.uiState.value.retryStep)
        repo.requestResult = ResultadoAuth.Exito(Unit)
        vm.solicitar("persona@example.invalid"); runCurrent()
        assertEquals(AuthStep.OTP, vm.uiState.value.step)
        repo.verifyResult = ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
        vm.verificar("test-code"); runCurrent()
        assertEquals(AuthStep.OTP, vm.uiState.value.retryStep)
        repo.verifyResult = ResultadoAuth.Exito(RecuperacionId("test-handle"))
        vm.verificar("test-code"); runCurrent()
        assertEquals(AuthStep.NUEVA_PASSWORD, vm.uiState.value.step)
        assertEquals(1, initializations)
    }
    @Test fun uncertainPasswordChangeNeverOffersEmailAsRetryOfThatChange() = runTest {
        val repo = FakeUiAuth(); val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.recuperar(); vm.solicitar("persona@example.invalid"); runCurrent(); vm.verificar("test-code"); runCurrent()
        repo.updateResult = ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
        vm.actualizar("test-password", "test-password"); runCurrent()
        assertEquals(AuthStep.CORREO, vm.uiState.value.step)
        assertNotEquals(vm.uiState.value.step, vm.uiState.value.retryStep)
    }

    @Test fun temporaryAuthFailureRetainsPrivateStoreUntilLogoutOrDifferentUser() = runTest {
        val repo = FakeUiAuth().apply { estadoSesion.value = EstadoSesion.Autenticada("A") }
        val vm = AuthViewModel(repo, { ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent()
        val original = vm.privateStores.obtain("A", vm.uiState.value.sessionVersion)
        repo.estadoSesion.value = EstadoSesion.TemporalmenteNoDisponible; runCurrent()
        repo.estadoSesion.value = EstadoSesion.Autenticada("A"); runCurrent()
        assertSame(original, vm.privateStores.obtain("A", vm.uiState.value.sessionVersion))
        repo.estadoSesion.value = EstadoSesion.SinSesion; runCurrent()
        repo.estadoSesion.value = EstadoSesion.Autenticada("B"); runCurrent()
        assertNotSame(original, vm.privateStores.obtain("B", vm.uiState.value.sessionVersion))
    }

    @Test fun confirmedAccountDeletionWaitsForBusyCoordinatorBeforeLocalLogout() = runTest {
        val repo = FakeUiAuth(); repo.estadoSesion.value = EstadoSesion.Autenticada("A")
        val release = CompletableDeferred<Unit>()
        val vm = AuthViewModel(repo, { release.await(); ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); assertTrue(vm.uiState.value.busy); vm.cuentaEliminada(); runCurrent()
        assertEquals(AuthDestination.INICIO, vm.uiState.value.destination)
        release.complete(Unit); runCurrent()
        assertEquals(AuthDestination.FORMULARIO, vm.uiState.value.destination)
    }
    @Test fun delayedAccountDeletionCleanupCannotLogoutAnotherAccount() = runTest {
        val repo = FakeUiAuth(); repo.estadoSesion.value = EstadoSesion.Autenticada("A")
        val release = CompletableDeferred<Unit>()
        val vm = AuthViewModel(repo, { release.await(); ResultadoAuth.Exito(Unit) }, backgroundScope)
        runCurrent(); vm.cuentaEliminada(); runCurrent()
        repo.estadoSesion.value = EstadoSesion.Autenticada("B"); runCurrent()
        release.complete(Unit); runCurrent()
        assertEquals(EstadoSesion.Autenticada("B"), vm.uiState.value.sesion)
    }

}
