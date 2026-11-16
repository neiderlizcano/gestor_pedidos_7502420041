package com.neiderlizcano.gestorpedidos.nativo.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Tipo de escritura sobre el perfil; sirve para señalar resultados inciertos. */
enum class EscrituraPerfil { CREAR, ACTUALIZAR, ELIMINAR }
/** Estado observable del perfil: datos, edición, errores, escritura incierta y confirmaciones. */
data class PerfilUiState(
    val perfil: Perfil? = null, val missing: Boolean = false, val editing: Boolean = false,
    val username: String = "", val nombre: String = "", val dirty: Boolean = false,
    val usernameRequired: Boolean = false, val nombreRequired: Boolean = false,
    val busy: Boolean = false, val error: AppError? = null, val writeFailure: Boolean = false,
    val uncertain: EscrituraPerfil? = null, val confirmDelete: Boolean = false, val deleted: Boolean = false,
    /** Verdadero tras guardar correctamente; se limpia al iniciar otra operación o al editar de nuevo. */
    val guardado: Boolean = false
)

/** Gestiona el perfil propio: consulta, creación explícita, edición y eliminación de cuenta con confirmación. */
class PerfilViewModel(
    private val repository: UsuariosRepository,
    private val onAccountDeleted: () -> Unit,
    testScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val state = MutableStateFlow(PerfilUiState())
    val uiState = state.asStateFlow()
    init { consultar() }
    private fun execute(action: suspend () -> Unit) {
        if (state.value.busy || state.value.deleted) return
        state.update { it.copy(busy = true, error = null, writeFailure = false, confirmDelete = false, guardado = false) }
        scope.launch { try { action() } finally { state.update { it.copy(busy = false) } } }
    }
    private fun failure(error: AppError, write: EscrituraPerfil? = null) {
        val uncertain = write != null && when (error) {
            AppError.ErrorConexion, AppError.RespuestaInvalida, AppError.ServicioNoDisponible -> true
            is AppError.ErrorHttp -> error.codigo == 408 || error.codigo in 500..599
            else -> false
        }
        state.update { it.copy(error = error, writeFailure = write != null,
            uncertain = if (uncertain) write else it.uncertain) }
    }
    fun consultar() = execute {
        when (val result = repository.consultarPerfilPropio()) {
            is AppResult.Exito -> state.update {
                it.copy(perfil = result.valor, missing = false,
                    username = if (it.dirty) it.username else result.valor.username,
                    nombre = if (it.dirty) it.nombre else result.valor.nombre,
                    uncertain = it.uncertain.takeIf { pending -> pending == EscrituraPerfil.ELIMINAR })
            }
            is AppResult.Fallo -> if (result.error == AppError.ErrorHttp(404)) state.update {
                // Un perfil ausente no demuestra que la cuenta Auth haya sido eliminada.
                it.copy(perfil = null, missing = true, editing = false,
                    uncertain = it.uncertain.takeIf { pending -> pending == EscrituraPerfil.ELIMINAR })
            } else failure(result.error)
        }
    }
    fun crear() {
        if (!state.value.busy && state.value.missing && state.value.uncertain == null)
            state.update { it.copy(editing = true, error = null, usernameRequired = false, nombreRequired = false) }
    }
    fun editar() {
        if (state.value.busy || state.value.uncertain != null) return
        val perfil = state.value.perfil ?: return
        state.update { it.copy(editing = true, username = if (it.dirty) it.username else perfil.username,
            nombre = if (it.dirty) it.nombre else perfil.nombre, error = null, guardado = false) }
    }
    fun cambiarUsername(value: String) { if (!state.value.busy) state.update { it.copy(username = value, dirty = true, usernameRequired = false) } }
    fun cambiarNombre(value: String) { if (!state.value.busy) state.update { it.copy(nombre = value, dirty = true, nombreRequired = false) } }
    fun cancelar() { if (!state.value.busy) state.update { it.copy(editing = false, usernameRequired = false, nombreRequired = false) } }
    fun guardar() {
        val current = state.value
        if (current.busy || current.deleted || !current.editing || current.uncertain != null) return
        if (current.username.isBlank() || current.nombre.isBlank()) {
            state.update { it.copy(usernameRequired = current.username.isBlank(), nombreRequired = current.nombre.isBlank()) }; return
        }
        val operation = if (current.perfil == null && current.missing) EscrituraPerfil.CREAR else EscrituraPerfil.ACTUALIZAR
        val datos = DatosPerfil(current.username.trim(), current.nombre.trim())
        execute {
            val result = if (operation == EscrituraPerfil.CREAR) repository.crearPerfil(datos) else repository.actualizarPerfilPropio(datos)
            when (result) {
                is AppResult.Fallo -> failure(result.error, operation)
                is AppResult.Exito -> state.update { it.copy(perfil = result.valor, missing = false, editing = false,
                    username = result.valor.username, nombre = result.valor.nombre, dirty = false, guardado = true) }
            }
        }
    }
    fun pedirEliminar() {
        if (!state.value.busy && !state.value.deleted && state.value.uncertain == null && (state.value.missing || state.value.perfil != null))
            state.update { it.copy(confirmDelete = true) }
    }
    fun cancelarEliminar() { state.update { it.copy(confirmDelete = false) } }
    fun eliminar() {
        if (!state.value.confirmDelete || state.value.uncertain != null) return
        execute {
            when (val result = repository.eliminarCuentaPropia()) {
                is AppResult.Fallo -> failure(result.error, EscrituraPerfil.ELIMINAR)
                is AppResult.Exito -> {
                    state.value = PerfilUiState(deleted = true, busy = true)
                    // El coordinador Auth externo limpia la sesión y todos los ViewModels privados.
                    onAccountDeleted()
                }
            }
        }
    }
}
