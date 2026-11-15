package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AuthStep { LOGIN, REGISTRO, REGISTRO_ENVIADO, CORREO, OTP, NUEVA_PASSWORD, COMPLETADA }
enum class AuthValidation { EMAIL, PASSWORD, CODIGO, CONFIRMACION }
enum class AuthDestination { RESTAURANDO, ERROR_RESTAURACION, FORMULARIO, INICIO }
data class AuthUiState(
    val sesion: EstadoSesion = EstadoSesion.Cargando,
    val step: AuthStep = AuthStep.LOGIN,
    val busy: Boolean = false,
    val sessionVersion: Long = 0,
    val error: ErrorAuth? = null,
    val validation: AuthValidation? = null,
    val invalidFields: Set<AuthValidation> = emptySet(),
    val retryStep: AuthStep? = null,
    val logoutNoConfirmado: Boolean = false
) {
    val destination: AuthDestination get() = when (sesion) {
        EstadoSesion.Cargando -> AuthDestination.RESTAURANDO
        EstadoSesion.TemporalmenteNoDisponible -> AuthDestination.ERROR_RESTAURACION
        is EstadoSesion.Autenticada -> AuthDestination.INICIO
        EstadoSesion.SinSesion -> AuthDestination.FORMULARIO
    }
}

class AuthViewModel(
    private val repository: AuthRepository,
    private val initialize: suspend () -> ResultadoAuth<Unit>,
    testScope: CoroutineScope? = null
) : ViewModel() {
    internal val formMemory = AuthFormMemory()
    internal val privateStores = RetainedSessionStores()
    override fun onCleared() { formMemory.clear(); privateStores.clear() }
    private val work = testScope ?: viewModelScope
    private val state = MutableStateFlow(AuthUiState())
    val uiState = state.asStateFlow()
    private var privateUser: String? = null
    private var email = ""
    private var recoveryId: RecuperacionId? = null
    init {
        work.launch { repository.estadoSesion.collect { session ->
            val previous = state.value.sesion
            val identityChanged = previous is EstadoSesion.Autenticada && previous != session ||
                session is EstadoSesion.Autenticada && previous != session
            val boundary = when (session) {
                is EstadoSesion.Autenticada -> privateUser != session.usuarioId
                EstadoSesion.SinSesion -> privateUser != null
                else -> false
            }
            if (boundary) { formMemory.clear(); privateStores.clear() }
            if (session is EstadoSesion.Autenticada) privateUser = session.usuarioId
            if (session == EstadoSesion.SinSesion) privateUser = null
            if (identityChanged) { recoveryId = null; email = "" }
            state.update { if (identityChanged) AuthUiState(sesion = session, busy = it.busy, sessionVersion = it.sessionVersion + if (boundary) 1 else 0,
                logoutNoConfirmado = if (session is EstadoSesion.Autenticada) false else it.logoutNoConfirmado) else it.copy(sesion = session) }
        } }
        reintentar()
    }
    private fun execute(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null, validation = null, invalidFields = emptySet(), retryStep = null) }
        work.launch { try { action() } finally { state.update { it.copy(busy = false) } } }
    }
    private fun validEmail(value: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(value)
    private fun invalid(error: AuthValidation) { state.update { it.copy(validation = error, invalidFields = setOf(error), error = null, retryStep = null) } }
    private fun failure(result: ResultadoAuth<*>, step: AuthStep? = null) {
        if (result is ResultadoAuth.Fallo) state.update { it.copy(error = result.error, retryStep = step) }
    }
    fun corregirCampo(field: AuthValidation) {
        state.update {
            val remaining = it.invalidFields - field
            it.copy(invalidFields = remaining, validation = if (it.validation == field) remaining.firstOrNull() else it.validation, error = null)
        }
    }
    fun reintentar() = execute { failure(initialize()) }
    fun login(email: String, password: String) {
        if (state.value.busy) return
        val issues = buildSet {
            if (!validEmail(email.trim())) add(AuthValidation.EMAIL)
            if (password.isBlank()) add(AuthValidation.PASSWORD)
        }
        if (issues.isNotEmpty()) {
            state.update { it.copy(validation = issues.first(), invalidFields = issues, error = null) }; return
        }
        execute {
            privateStores.clear()
            state.update { it.copy(sessionVersion = it.sessionVersion + 1) }
            failure(repository.iniciarSesion(email.trim(), password), AuthStep.LOGIN)
        }
    }
    fun abrirRegistro() {
        if (!state.value.busy) state.update { it.copy(step = AuthStep.REGISTRO, error = null, validation = null, invalidFields = emptySet()) }
    }
    fun registrar(email: String, password: String, confirmation: String) {
        if (state.value.busy) return
        if (!validEmail(email.trim())) return invalid(AuthValidation.EMAIL)
        if (password.isBlank()) return invalid(AuthValidation.PASSWORD)
        if (password != confirmation) return invalid(AuthValidation.CONFIRMACION)
        execute {
            when (val result = repository.registrar(email.trim(), password)) {
                is ResultadoAuth.Fallo -> failure(result)
                is ResultadoAuth.Exito -> state.update { it.copy(step = AuthStep.REGISTRO_ENVIADO) }
            }
        }
    }
    fun recuperar() {
        if (state.value.busy) return
        recoveryId = null; email = ""
        state.update { it.copy(step = AuthStep.CORREO, error = null, validation = null, invalidFields = emptySet(), logoutNoConfirmado = false) }
    }
    fun solicitar(email: String) {
        if (state.value.busy) return
        if (!validEmail(email.trim())) return invalid(AuthValidation.EMAIL)
        execute {
            when (val result = repository.solicitarRecuperacion(email.trim())) {
                is ResultadoAuth.Fallo -> failure(result, AuthStep.CORREO)
                is ResultadoAuth.Exito -> { this@AuthViewModel.email = email.trim(); state.update { it.copy(step = AuthStep.OTP) } }
            }
        }
    }
    fun verificar(codigo: String) {
        if (state.value.busy) return
        if (codigo.isBlank()) return invalid(AuthValidation.CODIGO)
        execute {
            when (val result = repository.verificarOtp(email, codigo)) {
                is ResultadoAuth.Fallo -> failure(result, AuthStep.OTP)
                is ResultadoAuth.Exito -> { recoveryId = result.valor; state.update { it.copy(step = AuthStep.NUEVA_PASSWORD) } }
            }
        }
    }
    fun actualizar(password: String, confirmacion: String) {
        if (state.value.busy) return
        if (password.isBlank()) return invalid(AuthValidation.PASSWORD)
        if (password != confirmacion) return invalid(AuthValidation.CONFIRMACION)
        val id = recoveryId ?: run {
            state.update { it.copy(step = AuthStep.CORREO, error = ErrorAuth.OTP_INVALIDO_O_EXPIRADO, validation = null, invalidFields = emptySet()) }
            return
        }
        execute {
            when (val result = repository.actualizarPassword(id, password)) {
                is ResultadoAuth.Exito -> { recoveryId = null; email = ""; state.update { it.copy(step = AuthStep.COMPLETADA) } }
                is ResultadoAuth.Fallo -> {
                    failure(result, AuthStep.NUEVA_PASSWORD)
                    if (result.error != ErrorAuth.ENTRADA_RECHAZADA) {
                        recoveryId = null; email = ""; repository.cancelarRecuperacion()
                        state.update { it.copy(step = AuthStep.CORREO) }
                    }
                }
            }
        }
    }
    fun volverLogin() {
        if (state.value.busy) return
        execute {
            repository.cancelarRecuperacion(); recoveryId = null; email = ""
            state.update { it.copy(step = AuthStep.LOGIN, error = null, validation = null, invalidFields = emptySet()) }
        }
    }
    fun cuentaEliminada() {
        val user = (state.value.sesion as? EstadoSesion.Autenticada)?.usuarioId ?: return
        val version = state.value.sessionVersion
        // Espera al coordinador sin perder la limpieza ni cerrar una cuenta posterior.
        work.launch {
            uiState.first { !it.busy }
            if (privateUser == user && state.value.sessionVersion == version) logout()
        }
    }
    fun logout() = execute {
        formMemory.clear()
        privateStores.clear()
            state.update { it.copy(sessionVersion = it.sessionVersion + 1) }
        recoveryId = null; email = ""
        when (val result = repository.cerrarSesion()) {
            is ResultadoAuth.Fallo -> failure(result)
            is ResultadoAuth.Exito -> state.update { it.copy(step = AuthStep.LOGIN,
                logoutNoConfirmado = result.valor.revocacionRemota == RevocacionRemota.NO_CONFIRMADA) }
        }
    }
}
