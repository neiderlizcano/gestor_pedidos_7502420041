package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.session.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SessionManagerTest {
    private val clock = TestClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val remote = FakeAuthRemote()
    private val store = MemoryStore()
    private fun manager() = CoordinatedSessionManager(remote, store, scope, clock)
    @After fun release() { scope.cancel() }
    private suspend fun unavailable(block: suspend () -> Unit) {
        try { block(); fail("Se esperaba indisponibilidad") } catch (_: TokenUnavailableException) { }
    }
    @Test fun initialLoadIsIdempotentAndEmptySessionDoesNotCallRemote() = runBlocking {
        val manager = manager()
        assertEquals(EstadoSesion.Cargando, manager.estadoSesion.value)
        assertEquals(ResultadoAuth.Exito(Unit), manager.inicializar())
        manager.inicializar()
        assertNull(manager.tokenVigente())
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
        assertEquals(1, store.reads)
        assertEquals(0, remote.refreshes)
    }
    @Test fun restartRestoresDurableSessionAndProviderUsesItsToken() = runBlocking {
        manager().instalarSesion(session(clock))
        val restarted = manager()
        assertEquals("access-test-only", SessionAccessTokenProvider(restarted).accessToken())
        assertEquals(EstadoSesion.Autenticada(AUTH_USER), restarted.estadoSesion.value)
        assertEquals(0, remote.refreshes)
    }
    @Test fun expirationTriggersRotationAndPersistsBothNewTokens() = runBlocking {
        val manager = manager()
        manager.instalarSesion(session(clock, seconds = 61))
        clock.now = clock.now.plusSeconds(2)
        remote.refreshCall = { token ->
            assertEquals("refresh-test-only", token)
            assertTrue(store.saved!!.renovacionPendiente)
            ResultadoAuth.Exito(session(clock, "new-access", "new-refresh"))
        }
        assertEquals("new-access", manager.tokenVigente())
        assertEquals("new-refresh", store.saved!!.sesion.refreshToken)
        assertFalse(store.saved!!.renovacionPendiente)
        assertEquals("new-access", manager().tokenVigente())
        assertEquals(1, remote.refreshes)
    }
    @Test fun concurrentRequestsShareExactlyOneRefresh() = runBlocking {
        val manager = manager()
        manager.instalarSesion(session(clock, seconds = -1))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "rotated")) }
        val callers = (1..40).map { async(Dispatchers.Default) { manager.tokenVigente() } }
        withTimeout(3000) { entered.await() }
        release.complete(Unit)
        assertTrue(withTimeout(3000) { callers.awaitAll() }.all { it == "rotated" })
        assertEquals(1, remote.refreshes)
    }
    @Test fun cancelledWaiterDoesNotCancelSharedRefresh() = runBlocking {
        val manager = manager()
        manager.instalarSesion(session(clock, seconds = 0))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "rotated")) }
        val first = async { manager.tokenVigente() }
        withTimeout(3000) { entered.await() }
        val second = async { manager.tokenVigente() }
        first.cancelAndJoin()
        release.complete(Unit)
        assertEquals("rotated", withTimeout(3000) { second.await() })
        assertEquals(1, remote.refreshes)
    }
    @Test fun logoutDuringRefreshNeverRestoresOldSession() = runBlocking {
        val manager = manager()
        manager.instalarSesion(session(clock, seconds = -1))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "late-token")) }
        val pending = async { manager.tokenVigente() }
        withTimeout(3000) { entered.await() }
        assertEquals(ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.CONFIRMADA)), manager.cerrarSesion())
        release.complete(Unit)
        assertNull(withTimeout(3000) { pending.await() })
        assertNull(store.saved)
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
    }
    @Test fun logoutDuringLoginDiscardsLateCredentials() = runBlocking {
        val manager = manager(); val repository = RestAuthRepository(remote, manager)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.loginCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock)) }
        val login = async { repository.iniciarSesion("persona@example.invalid", "test-password") }
        withTimeout(3000) { entered.await() }
        repository.cerrarSesion()
        release.complete(Unit)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA), withTimeout(3000) { login.await() })
        assertNull(store.saved)
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
    }
    @Test fun newerLoginWinsOverOldLogin() = runBlocking {
        val manager = manager(); val repo = RestAuthRepository(remote, manager)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.loginCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "old")) }
        val old = async { repo.iniciarSesion("old@example.invalid", "test-password") }
        entered.await()
        remote.loginCall = { ResultadoAuth.Exito(session(clock, "new")) }
        assertEquals(ResultadoAuth.Exito(Unit), repo.iniciarSesion("new@example.invalid", "test-password"))
        release.complete(Unit)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA), old.await())
        assertEquals("new", manager.tokenVigente())
    }
    @Test fun newLoginDuringRefreshCannotBeOverwritten() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "obsolete")) }
        val old = async { manager.tokenVigente() }
        entered.await()
        manager.instalarSesion(session(clock, "new-login"))
        release.complete(Unit)
        assertNull(old.await())
        assertEquals("new-login", manager.tokenVigente())
    }
    @Test fun temporaryRefreshFailureRetainsEncryptedStateWithoutRetryOrLogout() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE) }
        unavailable { manager.tokenVigente() }
        unavailable { manager.tokenVigente() }
        assertEquals(EstadoSesion.TemporalmenteNoDisponible, manager.estadoSesion.value)
        assertNotNull(store.saved)
        assertTrue(store.saved!!.renovacionPendiente)
        assertEquals(1, remote.refreshes)
        unavailable { manager().tokenVigente() }
        assertEquals(1, remote.refreshes)
    }
    @Test fun transportFailureBecomesSanitizedIoAndIsNotRetried() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.CONEXION) }
        try { manager.tokenVigente(); fail("Se esperaba IOException") } catch (_: IOException) { }
        assertEquals(1, remote.refreshes)
        assertNotNull(store.saved)
    }
    @Test fun recognizedRevocationClearsDurableSession() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA) }
        assertNull(manager.tokenVigente())
        assertNull(store.saved)
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
    }
    @Test fun unrecognizedRefreshErrorDoesNotEraseSession() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.NO_CLASIFICADO) }
        unavailable { manager.tokenVigente() }
        assertNotNull(store.saved)
    }
    @Test fun failedMarkPreventsSendingRefresh() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        store.failWriteAt = 2
        unavailable { manager.tokenVigente() }
        assertEquals(0, remote.refreshes)
    }
    @Test fun failedPersistenceAfterRotationNeverPublishesOrReturnsNewToken() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        store.failWriteAt = 3
        remote.refreshCall = { ResultadoAuth.Exito(session(clock, "not-durable")) }
        unavailable { manager.tokenVigente() }
        assertEquals(EstadoSesion.TemporalmenteNoDisponible, manager.estadoSesion.value)
        assertTrue(store.saved!!.renovacionPendiente)
        unavailable { manager().tokenVigente() }
        assertEquals(1, remote.refreshes)
    }
    @Test fun restartWithCorruptedStorageBlocksInsteadOfTreatingItAsEmpty() = runBlocking {
        store.failRead = true
        val manager = manager()
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), manager.inicializar())
        unavailable { manager.tokenVigente() }
        assertEquals(EstadoSesion.TemporalmenteNoDisponible, manager.estadoSesion.value)
    }
    @Test fun remoteLogoutFailureStillClearsLocalSession() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock))
        remote.logoutResult = ResultadoAuth.Fallo(ErrorAuth.CONEXION)
        assertEquals(ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.NO_CONFIRMADA)), manager.cerrarSesion())
        assertNull(store.saved)
        assertNull(manager().tokenVigente())
    }
    @Test fun logoutWithoutSessionDoesNotCallRemote() = runBlocking {
        assertEquals(ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.NO_APLICA)), manager().cerrarSesion())
        assertEquals(0, remote.logouts)
    }
    @Test fun failedLocalDeletionIsNotReportedAsSuccessfulLogout() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock)); store.failDelete = true
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), manager.cerrarSesion())
        unavailable { manager.tokenVigente() }
        assertEquals(0, remote.logouts)
    }
    @Test fun refreshCannotSwitchUserIdentity() = runBlocking {
        val manager = manager(); manager.instalarSesion(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Exito(SesionInterna("00000000-0000-0000-0000-000000000002", "other", "other", clock.now.plusSeconds(3600))) }
        unavailable { manager.tokenVigente() }
        assertEquals(EstadoSesion.TemporalmenteNoDisponible, manager.estadoSesion.value)
    }
    @Test fun loginFailureDoesNotCreateSession() = runBlocking {
        val manager = manager()
        remote.loginCall = { ResultadoAuth.Fallo(ErrorAuth.CREDENCIALES_RECHAZADAS) }
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CREDENCIALES_RECHAZADAS), RestAuthRepository(remote, manager).iniciarSesion("persona@example.invalid", "test-password"))
        assertNull(store.saved)
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
    }
    @Test fun expiredStartupShowsLoadingAndRestoresWithoutBusinessCall() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        val manager = manager()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "startup-access")) }
        val start = async { manager.inicializar() }
        withTimeout(3000) { entered.await() }
        assertEquals(EstadoSesion.Cargando, manager.estadoSesion.value)
        release.complete(Unit)
        assertEquals(ResultadoAuth.Exito(Unit), start.await())
        assertEquals(EstadoSesion.Autenticada(AUTH_USER), manager.estadoSesion.value)
        assertEquals("startup-access", manager.tokenVigente())
        assertEquals(1, remote.refreshes)
    }
    @Test fun confirmedUnsentRefreshAllowsOnlyExplicitRetry() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        val manager = manager()
        remote.refreshCall = { throw RefreshNotSentException() }
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), manager.inicializar())
        assertFalse(store.saved!!.renovacionPendiente)
        try { manager.tokenVigente(); fail("No debe reintentar automáticamente") } catch (_: IOException) { }
        assertEquals(1, remote.refreshes)
        remote.refreshCall = { ResultadoAuth.Exito(session(clock, "retried-access")) }
        assertEquals(ResultadoAuth.Exito(Unit), manager.inicializar())
        assertEquals("retried-access", manager.tokenVigente())
        assertEquals(2, remote.refreshes)
    }
    @Test fun restartAfterUnsentRefreshCanRestoreNormally() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        remote.refreshCall = { throw RefreshNotSentException() }
        manager().inicializar()
        remote.refreshCall = { ResultadoAuth.Exito(session(clock)) }
        assertEquals(ResultadoAuth.Exito(Unit), manager().inicializar())
        assertEquals(2, remote.refreshes)
    }
    @Test fun uncertainRefreshCannotBeRetriedByInitializationOrRestart() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.CONEXION) }
        val manager = manager()
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), manager.inicializar())
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), manager.inicializar())
        assertTrue(store.saved!!.renovacionPendiente)
        assertTrue(manager().inicializar() is ResultadoAuth.Fallo)
        assertEquals(1, remote.refreshes)
    }
    @Test fun revokedStartupEndsWithoutSessionAndNeverRetries() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA) }
        val manager = manager()
        manager.inicializar()
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
        manager.inicializar()
        assertEquals(1, remote.refreshes)
    }
    @Test fun failedResetOfPendingFlagCannotAuthorizeRetry() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        store.failWriteAt = 2
        remote.refreshCall = { throw RefreshNotSentException() }
        val manager = manager()
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), manager.inicializar())
        assertTrue(store.saved!!.renovacionPendiente)
        manager.inicializar()
        assertEquals(1, remote.refreshes)
    }
    @Test fun logoutDuringRestorationNeverPublishesAuthenticatedState() = runBlocking {
        store.saved = SesionPersistida(session(clock, seconds = -1))
        val manager = manager()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock)) }
        val start = async { manager.inicializar() }
        entered.await()
        manager.cerrarSesion()
        release.complete(Unit)
        start.await()
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
        assertNull(store.saved)
    }

}
