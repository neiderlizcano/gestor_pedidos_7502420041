package com.neiderlizcano.gestorpedidos.nativo.domain.auth

import kotlinx.coroutines.flow.StateFlow

/** Contrato de autenticación: registro, inicio y cierre de sesión, recuperación por correo con código OTP y estado
 * de sesión observable. */
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

/** Estado de la sesión: cargando, sin sesión, autenticada o temporalmente no disponible. */
sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Autenticada(val usuarioId: String) : EstadoSesion
    data object TemporalmenteNoDisponible : EstadoSesion
}
/** Resultado de una operación de autenticación: [Exito] con su valor o [Fallo] con un [ErrorAuth]. */
sealed interface ResultadoAuth<out T> {
    data class Exito<T>(val valor: T) : ResultadoAuth<T>
    data class Fallo(val error: ErrorAuth) : ResultadoAuth<Nothing>
}
/** Motivos de fallo de la autenticación, sin texto de interfaz. */
enum class ErrorAuth {
    CREDENCIALES_RECHAZADAS, SESION_RECHAZADA, OTP_INVALIDO_O_EXPIRADO,
    ENTRADA_RECHAZADA, LIMITE_SOLICITUDES, CONEXION, SERVICIO_NO_DISPONIBLE,
    RESPUESTA_INVALIDA, ALMACENAMIENTO, NO_CLASIFICADO
}
/** Indica si el servidor confirmó la revocación de la sesión al cerrarla. */
enum class RevocacionRemota { CONFIRMADA, NO_CONFIRMADA, NO_APLICA }
/** Resultado de cerrar sesión: informa si la revocación remota se confirmó, no se confirmó o no aplicaba. */
data class ResultadoLogout(val revocacionRemota: RevocacionRemota)

/** Identificador opaco de un proceso de recuperación de contraseña ya verificado con el código OTP. */
class RecuperacionId internal constructor(internal val valor: String)
