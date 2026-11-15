package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.flow.MutableStateFlow

interface AuthRuntime {
    val authRepository: AuthRepository
    suspend fun inicializar(): ResultadoAuth<Unit>
}

// Una instalación sin configuración nunca autentica de forma simulada ni envía credenciales.
class UnavailableAuthRuntime(val configurationErrors: List<String> = emptyList()) : AuthRuntime {
    override val authRepository = object : AuthRepository {
        override val estadoSesion = MutableStateFlow<EstadoSesion>(EstadoSesion.SinSesion)
        override suspend fun iniciarSesion(email: String, password: String) = unavailable<Unit>()
        override suspend fun cerrarSesion() = unavailable<ResultadoLogout>()
        override suspend fun solicitarRecuperacion(email: String) = unavailable<Unit>()
        override suspend fun verificarOtp(email: String, codigo: String) = unavailable<RecuperacionId>()
        override suspend fun actualizarPassword(recuperacionId: RecuperacionId, nuevaPassword: String) = unavailable<Unit>()
        override suspend fun cancelarRecuperacion() { }
    }
    override suspend fun inicializar() = unavailable<Unit>()
    private fun <T> unavailable(): ResultadoAuth<T> = ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
}
