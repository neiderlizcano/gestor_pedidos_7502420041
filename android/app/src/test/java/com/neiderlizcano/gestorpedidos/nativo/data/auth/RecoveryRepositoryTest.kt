package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.session.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecoveryRepositoryTest {
    private val clock = TestClock()
    private val remote = FakeAuthRemote()
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = CoordinatedSessionManager(remote, store, scope, clock)
    private val repository = RestAuthRepository(remote, sessions, clock)
    private val email = "persona@example.invalid"
    @After fun release() { scope.cancel() }
    private suspend fun verify(): RecuperacionId {
        remote.recoverCall = { ResultadoAuth.Exito(Unit) }
        remote.verifyCall = { _, _ -> ResultadoAuth.Exito(session(clock, "recovery-access", "recovery-refresh")) }
        repository.solicitarRecuperacion(email)
        return (repository.verificarOtp(email, "test-code") as ResultadoAuth.Exito).valor
    }
    @Test fun recoveryNeverInstallsNormalSessionOrPersistsItsTokens() = runBlocking {
        verify()
        assertNull(SessionAccessTokenProvider(sessions).accessToken())
        assertNull(store.saved)
        assertEquals(EstadoSesion.SinSesion, repository.estadoSesion.value)
    }
    @Test fun successfulChangeUsesRecoveryTokenThenRequiresLogin() = runBlocking {
        val id = verify()
        remote.updateCall = { session, password ->
            assertEquals("recovery-access", session.accessToken)
            assertEquals("test-password", password)
            ResultadoAuth.Exito(Unit)
        }
        assertEquals(ResultadoAuth.Exito(Unit), repository.actualizarPassword(id, "test-password"))
        assertNull(store.saved)
        assertNull(sessions.tokenVigente())
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO), repository.actualizarPassword(id, "test-password"))
    }
    @Test fun cancelAndExpiryInvalidateOpaqueHandle() = runBlocking {
        val id = verify()
        repository.cancelarRecuperacion()
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO), repository.actualizarPassword(id, "test-password"))
        val expired = verify()
        clock.now = clock.now.plusSeconds(3601)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO), repository.actualizarPassword(expired, "test-password"))
    }
    @Test fun lateVerificationAfterCancelCannotCreateContext() = runBlocking {
        remote.recoverCall = { ResultadoAuth.Exito(Unit) }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.verifyCall = { _, _ -> entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock)) }
        repository.solicitarRecuperacion(email)
        val pending = async { repository.verificarOtp(email, "test-code") }
        entered.await(); repository.cancelarRecuperacion(); release.complete(Unit)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA), pending.await())
        assertNull(store.saved)
    }
    @Test fun failedUncertainUpdateCannotBeRepeatedWithSameHandle() = runBlocking {
        val id = verify(); var calls = 0
        remote.updateCall = { _, _ -> calls++; ResultadoAuth.Fallo(ErrorAuth.CONEXION) }
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), repository.actualizarPassword(id, "test-password"))
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO), repository.actualizarPassword(id, "test-password"))
        assertEquals(1, calls)
    }
    @Test fun rejectedInputCanBeCorrectedWithoutNewOtp() = runBlocking {
        val id = verify()
        remote.updateCall = { _, _ -> ResultadoAuth.Fallo(ErrorAuth.ENTRADA_RECHAZADA) }
        repository.actualizarPassword(id, "test-password")
        remote.updateCall = { _, _ -> ResultadoAuth.Exito(Unit) }
        assertEquals(ResultadoAuth.Exito(Unit), repository.actualizarPassword(id, "other-test-password"))
    }
    @Test fun codeForOtherEmailIsNotSubmitted() = runBlocking {
        remote.recoverCall = { ResultadoAuth.Exito(Unit) }
        repository.solicitarRecuperacion(email)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ENTRADA_RECHAZADA), repository.verificarOtp("other@example.invalid", "test-code"))
    }
    @Test fun processRecreationDoesNotRestoreRecoveryContext() = runBlocking {
        val id = verify()
        val recreated = RestAuthRepository(remote, sessions, clock)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO), recreated.actualizarPassword(id, "test-password"))
    }
}
