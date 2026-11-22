package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.session.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock

internal interface SessionManager {
    val estadoSesion: StateFlow<EstadoSesion>
    suspend fun inicializar(): ResultadoAuth<Unit>
    suspend fun instalarSesion(sesion: SesionInterna): ResultadoAuth<Unit>
    suspend fun tokenVigente(): String?
    suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout>
}

internal class CoordinatedSessionManager(
    private val remote: AuthRemote,
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC()
) : SessionManager {
    private val mutex = Mutex()
    private val state = MutableStateFlow<EstadoSesion>(EstadoSesion.Cargando)
    override val estadoSesion = state.asStateFlow()
    private var loaded = false
    private var session: SesionInterna? = null
    private var generation = 0L
    private var failure: ErrorAuth? = null
    private var retryable = false
    private var forceRenewal = false
    private var refreshing: Deferred<ResultadoAuth<SesionInterna>>? = null

    override suspend fun inicializar(): ResultadoAuth<Unit> {
        val ready = mutex.withLock {
            val loadedResult = load()
            if (failure != null && !retryable) return@withLock loadedResult
            if (retryable) { failure = null; retryable = false }
            val current = session
            if (current != null && (forceRenewal || !current.venceEn.isAfter(clock.instant().plusSeconds(60)))) {
                state.value = EstadoSesion.Cargando
            }
            ResultadoAuth.Exito(Unit)
        }
        if (ready is ResultadoAuth.Fallo) return ready
        return try {
            tokenVigente()
            mutex.withLock { failure?.let { ResultadoAuth.Fallo(it) } ?: ResultadoAuth.Exito(Unit) }
        } catch (_: TokenUnavailableException) {
            mutex.withLock { ResultadoAuth.Fallo(failure ?: ErrorAuth.SERVICIO_NO_DISPONIBLE) }
        } catch (_: IOException) { ResultadoAuth.Fallo(ErrorAuth.CONEXION) }
    }

    private suspend fun load(): ResultadoAuth<Unit> {
        if (loaded) return failure?.let { ResultadoAuth.Fallo(it) } ?: ResultadoAuth.Exito(Unit)
        return when (val saved = store.leer()) {
            is ResultadoAuth.Fallo -> { loaded = true; unavailable(saved.error); saved }
            is ResultadoAuth.Exito -> {
                loaded = true
                session = saved.valor?.sesion
                if (saved.valor?.renovacionPendiente == true) {
                    unavailable(ErrorAuth.SERVICIO_NO_DISPONIBLE)
                    ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
                } else { publish(); ResultadoAuth.Exito(Unit) }
            }
        }
    }

    private fun publish() {
        val current = session
        state.value = if (current == null) EstadoSesion.SinSesion
            else if (current.venceEn.isAfter(clock.instant())) EstadoSesion.Autenticada(current.usuarioId)
            else EstadoSesion.TemporalmenteNoDisponible
    }
    private fun unavailable(error: ErrorAuth) { retryable = false; failure = error; state.value = EstadoSesion.TemporalmenteNoDisponible }

    // La generación se toma antes del HTTP de login para descartar respuestas posteriores a logout.
    suspend fun comenzarLogin(): Long = mutex.withLock {
        load()
        generation++
        refreshing = null
        generation
    }
    suspend fun instalarSiVigente(sesion: SesionInterna, expected: Long): ResultadoAuth<Unit> = mutex.withLock {
        if (expected != generation) return@withLock ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
        save(sesion)
    }
    override suspend fun instalarSesion(sesion: SesionInterna): ResultadoAuth<Unit> = mutex.withLock {
        generation++
        refreshing = null
        save(sesion)
    }
    private suspend fun save(value: SesionInterna): ResultadoAuth<Unit> {
        currentCoroutineContext().ensureActive()
        // El cambio durable y su publicación forman una única sección crítica no cancelable.
        return withContext(NonCancellable) {
            when (val result = store.sustituir(SesionPersistida(value))) {
                is ResultadoAuth.Fallo -> { session = null; loaded = true; unavailable(result.error); result }
                is ResultadoAuth.Exito -> {
                    session = value; loaded = true; failure = null; retryable = false; forceRenewal = false; publish(); ResultadoAuth.Exito(Unit)
                }
            }
        }
    }

    override suspend fun tokenVigente(): String? {
        val operation = mutex.withLock {
            load()
            failure?.let { throwUnavailable(it) }
            val current = session ?: return null
            if (!forceRenewal && current.venceEn.isAfter(clock.instant().plusSeconds(60))) return current.accessToken
            val expected = generation
            refreshing ?: scope.async(start = CoroutineStart.LAZY) { renew(current, expected) }.also {
                refreshing = it
                it.start()
            }
        }
        val result = operation.await()
        return mutex.withLock {
            if (refreshing === operation) refreshing = null
            when (result) {
                is ResultadoAuth.Fallo -> {
                    if (result.error == ErrorAuth.SESION_RECHAZADA) null else throwUnavailable(result.error)
                }
                is ResultadoAuth.Exito -> {
                    // Un logout o login posterior invalida también a quienes aguardaban este refresh.
                    if (session === result.valor && failure == null) result.valor.accessToken else null
                }
            }
        }
    }

    suspend fun credential(): SessionCredential? {
        val expected = mutex.withLock { load(); generation }
        val token = tokenVigente() ?: return null
        // No capturar el token de otra cuenta si la sesión cambió mientras esperábamos.
        return mutex.withLock {
            if (expected == generation && failure == null && session?.accessToken == token) SessionCredential(token, generation) else null
        }
    }
    suspend fun isCurrent(credential: SessionCredential): Boolean = mutex.withLock {
        generation == credential.generation && session != null
    }
    suspend fun refreshAfterUnauthorized(rejected: SessionCredential): SessionCredential? {
        val operation = mutex.withLock {
            if (generation != rejected.generation || session == null) return null
            failure?.let { throwUnavailable(it) }
            val current = session!!
            if (current.accessToken != rejected.token) return SessionCredential(current.accessToken, generation)
            forceRenewal = true
            val expected = generation
            refreshing ?: scope.async(start = CoroutineStart.LAZY) { renew(current, expected) }.also {
                refreshing = it; it.start()
            }
        }
        val result = operation.await()
        return mutex.withLock {
            if (refreshing === operation) refreshing = null
            if (generation != rejected.generation) return@withLock null
            when (result) {
                is ResultadoAuth.Fallo -> if (result.error == ErrorAuth.SESION_RECHAZADA) null else throwUnavailable(result.error)
                is ResultadoAuth.Exito -> if (failure == null && session === result.valor)
                    SessionCredential(result.valor.accessToken, generation) else null
            }
        }
    }
    suspend fun invalidateIfCurrent(credential: SessionCredential) {
        withContext(NonCancellable) { mutex.withLock {
            if (generation != credential.generation || session?.accessToken != credential.token) return@withLock
            generation++; session = null; refreshing = null; loaded = true; retryable = false
            when (store.eliminar()) {
                is ResultadoAuth.Fallo -> { unavailable(ErrorAuth.ALMACENAMIENTO); throw TokenUnavailableException() }
                is ResultadoAuth.Exito -> { failure = null; state.value = EstadoSesion.SinSesion }
            }
        } }
    }

    private suspend fun renew(previous: SesionInterna, expected: Long): ResultadoAuth<SesionInterna> {
        val prepared = mutex.withLock {
            if (expected != generation || session !== previous) return ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
            // Persiste la intención antes de rotar: un reinicio no repetirá un refresh incierto.
            when (val mark = store.sustituir(SesionPersistida(previous, renovacionPendiente = true))) {
                is ResultadoAuth.Fallo -> { unavailable(mark.error); mark }
                is ResultadoAuth.Exito -> ResultadoAuth.Exito(Unit)
            }
        }
        if (prepared is ResultadoAuth.Fallo) return prepared
        val response = try { remote.refresh(previous.refreshToken) }
        catch (_: RefreshNotSentException) {
            return withContext(NonCancellable) { mutex.withLock {
                if (expected != generation || session !== previous) return@withLock ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
                when (val restored = store.sustituir(SesionPersistida(previous))) {
                    is ResultadoAuth.Fallo -> { unavailable(restored.error); retryable = false; restored }
                    is ResultadoAuth.Exito -> {
                        refreshing = null
                        unavailable(ErrorAuth.CONEXION)
                        retryable = true
                        ResultadoAuth.Fallo(ErrorAuth.CONEXION)
                    }
                }
            } }
        }
        catch (cancelled: CancellationException) {
            withContext(NonCancellable) { mutex.withLock {
                if (expected == generation) unavailable(ErrorAuth.SERVICIO_NO_DISPONIBLE)
            } }
            throw cancelled
        }
        return mutex.withLock {
            if (expected != generation || session !== previous) return@withLock ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
            when (response) {
                is ResultadoAuth.Exito -> {
                    if (response.valor.usuarioId != previous.usuarioId) {
                        unavailable(ErrorAuth.RESPUESTA_INVALIDA)
                        return@withLock ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA)
                    }
                    when (val stored = save(response.valor)) {
                        is ResultadoAuth.Fallo -> stored
                        is ResultadoAuth.Exito -> response
                    }
                }
                is ResultadoAuth.Fallo -> {
                    if (response.error == ErrorAuth.SESION_RECHAZADA) {
                        generation++; session = null
                        withContext(NonCancellable) {
                            when (store.eliminar()) {
                                is ResultadoAuth.Fallo -> unavailable(ErrorAuth.ALMACENAMIENTO)
                                is ResultadoAuth.Exito -> { failure = null; state.value = EstadoSesion.SinSesion }
                            }
                        }
                        if (failure != null) ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) else response
                    } else { unavailable(response.error); response }
                }
            }
        }
    }

    override suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout> {
        var credential: String? = null
        val cleared = withContext(NonCancellable) { mutex.withLock {
            load()
            credential = session?.accessToken
            generation++; refreshing = null; session = null; loaded = true; failure = null; retryable = false; forceRenewal = false
            state.value = EstadoSesion.SinSesion
            store.eliminar().also { if (it is ResultadoAuth.Fallo) unavailable(ErrorAuth.ALMACENAMIENTO) }
        } }
        if (cleared is ResultadoAuth.Fallo) return cleared
        val token = credential ?: return ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.NO_APLICA))
        val remoteResult = remote.logout(token)
        return ResultadoAuth.Exito(ResultadoLogout(
            if (remoteResult is ResultadoAuth.Exito) RevocacionRemota.CONFIRMADA else RevocacionRemota.NO_CONFIRMADA))
    }

    private fun throwUnavailable(error: ErrorAuth): Nothing {
        if (error == ErrorAuth.CONEXION) throw IOException("Conexión Auth no disponible.")
        throw TokenUnavailableException()
    }
}
