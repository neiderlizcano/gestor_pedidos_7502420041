package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.session.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal const val AUTH_USER = "00000000-0000-0000-0000-000000000001"
internal class TestClock(var now: Instant = Instant.parse("2026-11-15T12:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}
internal fun session(clock: Clock, access: String = "access-test-only", refresh: String = "refresh-test-only", seconds: Long = 3600) =
    SesionInterna(AUTH_USER, access, refresh, clock.instant().plusSeconds(seconds))
internal class MemoryStore(var saved: SesionPersistida? = null) : SessionStore {
    var writes = 0
    var reads = 0
    var failWriteAt = Int.MAX_VALUE
    var failDelete = false
    var failRead = false
    override suspend fun leer(): ResultadoAuth<SesionPersistida?> {
        reads++
        return if (failRead) ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) else ResultadoAuth.Exito(saved)
    }
    override suspend fun sustituir(sesion: SesionPersistida): ResultadoAuth<Unit> {
        writes++
        if (writes >= failWriteAt) return ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO)
        saved = sesion
        return ResultadoAuth.Exito(Unit)
    }
    override suspend fun eliminar(): ResultadoAuth<Unit> {
        if (failDelete) return ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO)
        saved = null
        return ResultadoAuth.Exito(Unit)
    }
}
internal class FakeAuthRemote : AuthRemote {
    var recoverCall: suspend (String) -> ResultadoAuth<Unit> = { error("Recuperación no configurada") }
    var verifyCall: suspend (String, String) -> ResultadoAuth<SesionInterna> = { _, _ -> error("OTP no configurado") }
    var updateCall: suspend (SesionInterna, String) -> ResultadoAuth<Unit> = { _, _ -> error("Actualización no configurada") }
    override suspend fun recover(email: String) = recoverCall(email)
    override suspend fun verify(email: String, codigo: String) = verifyCall(email, codigo)
    override suspend fun updatePassword(sesion: SesionInterna, password: String) = updateCall(sesion, password)
    var refreshes = 0
    var logouts = 0
    var loginCall: suspend () -> ResultadoAuth<SesionInterna> = { error("Login no configurado en la prueba") }
    var refreshCall: suspend (String) -> ResultadoAuth<SesionInterna> = { error("Refresh no configurado en la prueba") }
    var logoutResult: ResultadoAuth<Unit> = ResultadoAuth.Exito(Unit)
    override suspend fun login(email: String, password: String) = loginCall()
    override suspend fun refresh(refreshToken: String): ResultadoAuth<SesionInterna> {
        refreshes++
        return refreshCall(refreshToken)
    }
    override suspend fun logout(accessToken: String): ResultadoAuth<Unit> { logouts++; return logoutResult }
}
