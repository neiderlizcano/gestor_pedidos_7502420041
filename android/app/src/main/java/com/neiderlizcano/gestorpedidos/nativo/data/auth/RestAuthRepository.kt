package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.session.SessionTokenProvider
import com.neiderlizcano.gestorpedidos.nativo.core.session.SessionCredential
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import com.neiderlizcano.gestorpedidos.nativo.core.session.SesionInterna
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID

internal class RestAuthRepository(
    private val remote: AuthRemote,
    private val sessions: CoordinatedSessionManager,
    private val clock: Clock = Clock.systemUTC()
) : AuthRepository {
    private val recoveryMutex = Mutex()
    private var generation = 0L
    private var requestedEmail: String? = null
    private class Recovery(val id: RecuperacionId, val session: SesionInterna)
    private var recovery: Recovery? = null
    override val estadoSesion = sessions.estadoSesion
    override suspend fun registrar(email: String, password: String): ResultadoAuth<Boolean> {
        cancelarRecuperacion()
        val stamp = sessions.comenzarLogin()
        return when (val result = remote.signup(email, password)) {
            is ResultadoAuth.Fallo -> result
            is ResultadoAuth.Exito -> if (result.valor == null) ResultadoAuth.Exito(true) else {
                when (val installed = sessions.instalarSiVigente(result.valor, stamp)) {
                    is ResultadoAuth.Fallo -> installed
                    is ResultadoAuth.Exito -> ResultadoAuth.Exito(false)
                }
            }
        }
    }
    override suspend fun iniciarSesion(email: String, password: String): ResultadoAuth<Unit> {
        cancelarRecuperacion()
        val generation = sessions.comenzarLogin()
        return when (val result = remote.login(email, password)) {
            is ResultadoAuth.Fallo -> result
            is ResultadoAuth.Exito -> sessions.instalarSiVigente(result.valor, generation)
        }
    }
    override suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout> {
        cancelarRecuperacion()
        return sessions.cerrarSesion()
    }
    override suspend fun cancelarRecuperacion() {
        recoveryMutex.withLock { generation++; recovery = null; requestedEmail = null }
    }
    override suspend fun solicitarRecuperacion(email: String): ResultadoAuth<Unit> {
        val stamp = recoveryMutex.withLock { generation++; recovery = null; requestedEmail = null; generation }
        val result = remote.recover(email)
        return recoveryMutex.withLock {
            if (stamp != generation) ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
            else { if (result is ResultadoAuth.Exito) requestedEmail = email; result }
        }
    }
    override suspend fun verificarOtp(email: String, codigo: String): ResultadoAuth<RecuperacionId> {
        val stamp = recoveryMutex.withLock {
            if (email != requestedEmail || codigo.isBlank()) return ResultadoAuth.Fallo(ErrorAuth.ENTRADA_RECHAZADA)
            generation++; recovery = null; generation
        }
        val result = remote.verify(email, codigo)
        return recoveryMutex.withLock {
            if (stamp != generation) return@withLock ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
            when (result) {
                is ResultadoAuth.Fallo -> result
                is ResultadoAuth.Exito -> {
                    if (!result.valor.venceEn.isAfter(clock.instant())) return@withLock ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO)
                    val id = RecuperacionId(UUID.randomUUID().toString())
                    // La sesión OTP permanece solo en memoria y nunca se instala en SessionManager.
                    recovery = Recovery(id, result.valor)
                    ResultadoAuth.Exito(id)
                }
            }
        }
    }
    override suspend fun actualizarPassword(recuperacionId: RecuperacionId, nuevaPassword: String): ResultadoAuth<Unit> {
        val (context, stamp) = recoveryMutex.withLock {
            val current = recovery
            if (current == null || current.id.valor != recuperacionId.valor || !current.session.venceEn.isAfter(clock.instant())) {
                recovery = null
                return ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO)
            }
            // Un solo intento por contexto evita repetir una actualización con resultado incierto.
            recovery = null
            Pair(current, generation)
        }
        val result = remote.updatePassword(context.session, nuevaPassword)
        return recoveryMutex.withLock {
            if (stamp != generation) return@withLock ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA)
            if (result is ResultadoAuth.Exito) {
                generation++; requestedEmail = null
                when (val cleared = sessions.cerrarSesion()) {
                    is ResultadoAuth.Fallo -> cleared
                    is ResultadoAuth.Exito -> ResultadoAuth.Exito(Unit)
                }
            } else {
                if (result == ResultadoAuth.Fallo(ErrorAuth.ENTRADA_RECHAZADA) && context.session.venceEn.isAfter(clock.instant())) recovery = context
                result
            }
        }
    }
}

internal class SessionAccessTokenProvider(private val sessions: CoordinatedSessionManager) : SessionTokenProvider {
    override suspend fun credential() = sessions.credential()
    override suspend fun isCurrent(credential: SessionCredential) = sessions.isCurrent(credential)
    override suspend fun refreshAfterUnauthorized(rejected: SessionCredential) = sessions.refreshAfterUnauthorized(rejected)
    override suspend fun invalidateIfCurrent(credential: SessionCredential) = sessions.invalidateIfCurrent(credential)
    override suspend fun accessToken(): String? = sessions.tokenVigente()
}
