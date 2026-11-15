package com.neiderlizcano.gestorpedidos.nativo.domain.auth

import kotlinx.coroutines.flow.StateFlow

// F1-C03A: las firmas OTP se incorporarán en F1-C03B según la autorización del estudiante.
interface AuthRepository {
    // true indica confirmación pendiente; false indica sesión instalada por el servidor.
    suspend fun registrar(email: String, password: String): ResultadoAuth<Boolean> = ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
    val estadoSesion: StateFlow<EstadoSesion>
    suspend fun iniciarSesion(email: String, password: String): ResultadoAuth<Unit>
    suspend fun cerrarSesion(): ResultadoAuth<ResultadoLogout>
    suspend fun solicitarRecuperacion(email: String): ResultadoAuth<Unit>
    suspend fun verificarOtp(email: String, codigo: String): ResultadoAuth<RecuperacionId>
    suspend fun actualizarPassword(recuperacionId: RecuperacionId, nuevaPassword: String): ResultadoAuth<Unit>
    suspend fun cancelarRecuperacion(): Unit
}

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Autenticada(val usuarioId: String) : EstadoSesion
    data object TemporalmenteNoDisponible : EstadoSesion
}
sealed interface ResultadoAuth<out T> {
    data class Exito<T>(val valor: T) : ResultadoAuth<T>
    data class Fallo(val error: ErrorAuth) : ResultadoAuth<Nothing>
}
enum class ErrorAuth {
    CREDENCIALES_RECHAZADAS, SESION_RECHAZADA, OTP_INVALIDO_O_EXPIRADO,
    ENTRADA_RECHAZADA, LIMITE_SOLICITUDES, CONEXION, SERVICIO_NO_DISPONIBLE,
    RESPUESTA_INVALIDA, ALMACENAMIENTO, NO_CLASIFICADO
}
enum class RevocacionRemota { CONFIRMADA, NO_CONFIRMADA, NO_APLICA }
data class ResultadoLogout(val revocacionRemota: RevocacionRemota)

class RecuperacionId internal constructor(internal val valor: String)
