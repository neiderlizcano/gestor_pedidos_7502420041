package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiRequestExecutor
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.core.session.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class UnauthorizedSessionTest {
    private val clock = TestClock()
    private val remote = FakeAuthRemote()
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manager = CoordinatedSessionManager(remote, store, scope, clock)
    private val provider = SessionAccessTokenProvider(manager)
    private val executor = ApiRequestExecutor(provider)
    private fun rejected(): Response<String> = Response.error(401, "".toResponseBody())
    @After fun release() { scope.cancel() }
    @Test fun read401ForcesRefreshOfLocallyValidTokenAndRetriesOnce() = runBlocking {
        manager.instalarSesion(session(clock))
        remote.refreshCall = { ResultadoAuth.Exito(session(clock, "renewed")) }
        var calls = 0
        val result = executor.executeRead({ bearer ->
            calls++
            if (calls == 1) rejected() else { assertEquals("Bearer renewed", bearer); Response.success("ok") }
        }) { it }
        assertEquals(AppResult.Exito("ok"), result)
        assertEquals(2, calls); assertEquals(1, remote.refreshes)
    }
    @Test fun writesAndDeletesAreNeverRepeated() = runBlocking {
        for (delete in listOf(false, true)) {
            manager.instalarSesion(session(clock))
            remote.refreshCall = { ResultadoAuth.Exito(session(clock, "renewed")) }
            var calls = 0
            val result = if (delete) executor.executeEmpty { calls++; Response.error<Unit>(401, "".toResponseBody()) }
                else executor.execute({ calls++; rejected() }) { it }
            assertEquals(AppResult.Fallo(AppError.SesionNoValida), result)
            assertEquals(1, calls)
            assertEquals("renewed", provider.accessToken())
        }
    }
    @Test fun second401InvalidatesOnlyTheStillCurrentSession() = runBlocking {
        manager.instalarSesion(session(clock))
        remote.refreshCall = { ResultadoAuth.Exito(session(clock, "renewed")) }
        var calls = 0
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), executor.executeRead({ calls++; rejected() }) { it })
        assertEquals(2, calls); assertNull(store.saved)
        assertEquals(EstadoSesion.SinSesion, manager.estadoSesion.value)
    }
    @Test fun temporaryRefreshFailureRetainsSessionAndDoesNotRetryRead() = runBlocking {
        manager.instalarSesion(session(clock))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE) }
        var calls = 0
        assertEquals(AppResult.Fallo(AppError.ServicioNoDisponible), executor.executeRead({ calls++; rejected() }) { it })
        assertEquals(1, calls); assertNotNull(store.saved)
        assertEquals(EstadoSesion.TemporalmenteNoDisponible, manager.estadoSesion.value)
    }
    @Test fun definitiveRefreshRejectionEndsSessionWithoutSecondRead() = runBlocking {
        manager.instalarSesion(session(clock))
        remote.refreshCall = { ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA) }
        var calls = 0
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), executor.executeRead({ calls++; rejected() }) { it })
        assertEquals(1, calls); assertNull(store.saved)
    }
    @Test fun concurrent401ShareOneRotationAndNoLoops() = runBlocking {
        manager.instalarSesion(session(clock))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        remote.refreshCall = { entered.complete(Unit); release.await(); ResultadoAuth.Exito(session(clock, "renewed")) }
        val jobs = (1..20).map { async(Dispatchers.Default) {
            executor.executeRead({ bearer -> if (bearer == "Bearer renewed") Response.success("ok") else rejected() }) { it }
        } }
        withTimeout(3000) { entered.await() }; release.complete(Unit)
        assertTrue(withTimeout(5000) { jobs.awaitAll() }.all { it == AppResult.Exito("ok") })
        assertEquals(1, remote.refreshes)
    }
    @Test fun lateSuccessFromAccountADoesNotReachAccountB() = runBlocking {
        manager.instalarSesion(session(clock))
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val request = async { executor.executeRead({ entered.complete(Unit); release.await(); Response.success("private-A") }) { it } }
        entered.await(); manager.cerrarSesion()
        manager.instalarSesion(SesionInterna("00000000-0000-0000-0000-000000000002", "B", "refresh-B", clock.now.plusSeconds(3600)))
        release.complete(Unit)
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), request.await())
        assertEquals("B", provider.accessToken())
    }
    @Test fun staleSecond401CannotInvalidateNewLogin() = runBlocking {
        manager.instalarSesion(session(clock))
        remote.refreshCall = { ResultadoAuth.Exito(session(clock, "renewed")) }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var calls = 0
        val pending = async { executor.executeRead({
            calls++; if (calls == 2) { entered.complete(Unit); release.await() }; rejected()
        }) { it } }
        entered.await(); manager.cerrarSesion(); manager.instalarSesion(session(clock, "new-login"))
        release.complete(Unit)
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), pending.await())
        assertEquals("new-login", provider.accessToken())
    }
    @Test fun retryAfterProvenUnsentFailureStillForcesRotation() = runBlocking {
        manager.instalarSesion(session(clock))
        remote.refreshCall = { throw RefreshNotSentException() }
        assertEquals(AppResult.Fallo(AppError.ErrorConexion), executor.executeRead({ rejected() }) { it })
        remote.refreshCall = { ResultadoAuth.Exito(session(clock, "recovered")) }
        assertEquals(ResultadoAuth.Exito(Unit), manager.inicializar())
        assertEquals("recovered", provider.accessToken()); assertEquals(2, remote.refreshes)
    }
}
