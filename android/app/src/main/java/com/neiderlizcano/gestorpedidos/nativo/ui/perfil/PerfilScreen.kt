package com.neiderlizcano.gestorpedidos.nativo.ui.perfil

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.UsuariosRepository

@Composable
fun PerfilRoute(repository: UsuariosRepository, onAccountDeleted: () -> Unit) {
    val factory = remember(repository, onAccountDeleted) { object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == PerfilViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return PerfilViewModel(repository, onAccountDeleted) as T
        }
    } }
    val vm: PerfilViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    PerfilScreen(state, vm)
}
@Composable
internal fun PerfilScreen(state: PerfilUiState, vm: PerfilViewModel) {
    BackHandler(enabled = state.editing || state.busy) { if (!state.busy) vm.cancelar() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp).testTag("perfilScreen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.perfil_title), style = MaterialTheme.typography.headlineMedium)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("perfilProgress"))
        state.error?.let { error ->
            Text(stringResource(errorMessage(error, state.writeFailure)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("perfilError"))
        }
        if (state.uncertain != null) Text(stringResource(if (state.uncertain == EscrituraPerfil.ELIMINAR)
            R.string.perfil_delete_uncertain else R.string.perfil_write_uncertain), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("perfilUncertain"))
        if (state.deleted) Text(stringResource(R.string.perfil_deleted)) else {
            TextButton(onClick = vm::consultar, enabled = !state.busy, modifier = Modifier.testTag("perfilConsultar")) { Text(stringResource(R.string.perfil_refresh)) }
            if (state.editing) {
                OutlinedTextField(state.username, vm::cambiarUsername, enabled = !state.busy, singleLine = true,
                    label = { Text(stringResource(R.string.perfil_username)) }, isError = state.usernameRequired,
                    supportingText = { if (state.usernameRequired) Text(stringResource(R.string.perfil_required)) },
                    modifier = Modifier.fillMaxWidth().testTag("perfilUsername"))
                OutlinedTextField(state.nombre, vm::cambiarNombre, enabled = !state.busy, singleLine = true,
                    label = { Text(stringResource(R.string.perfil_nombre)) }, isError = state.nombreRequired,
                    supportingText = { if (state.nombreRequired) Text(stringResource(R.string.perfil_required)) },
                    modifier = Modifier.fillMaxWidth().testTag("perfilNombre"))
                Text(stringResource(R.string.perfil_email_readonly))
                Text(state.perfil?.email ?: stringResource(R.string.perfil_email_auth), modifier = Modifier.testTag("perfilCorreo"))
                Button(onClick = vm::guardar, enabled = !state.busy && state.uncertain == null, modifier = Modifier.testTag("perfilGuardar")) { Text(stringResource(R.string.perfil_save)) }
                TextButton(onClick = vm::cancelar, enabled = !state.busy, modifier = Modifier.testTag("perfilCancelar")) { Text(stringResource(R.string.perfil_cancel)) }
            } else {
                if (state.missing) {
                    Text(stringResource(R.string.perfil_missing), modifier = Modifier.testTag("perfilMissing"))
                    Button(onClick = vm::crear, enabled = !state.busy && state.uncertain == null, modifier = Modifier.testTag("perfilCrear")) { Text(stringResource(R.string.perfil_create)) }
                }
                state.perfil?.let { perfil ->
                    Text(stringResource(R.string.perfil_username)); Text(perfil.username, modifier = Modifier.testTag("perfilUsernameValue"))
                    Text(stringResource(R.string.perfil_nombre)); Text(perfil.nombre, modifier = Modifier.testTag("perfilNombreValue"))
                    Text(stringResource(R.string.perfil_email_readonly)); Text(perfil.email, modifier = Modifier.testTag("perfilCorreo"))
                    Button(onClick = vm::editar, enabled = !state.busy && state.uncertain == null, modifier = Modifier.testTag("perfilEditar")) { Text(stringResource(R.string.perfil_edit)) }
                }
                if (state.missing || state.perfil != null) OutlinedButton(onClick = vm::pedirEliminar,
                    enabled = !state.busy && state.uncertain == null, modifier = Modifier.testTag("perfilEliminar")) { Text(stringResource(R.string.perfil_delete)) }
            }
        }
    }
    if (state.confirmDelete) AlertDialog(onDismissRequest = vm::cancelarEliminar,
        title = { Text(stringResource(R.string.perfil_delete)) }, text = { Text(stringResource(R.string.perfil_delete_warning)) },
        confirmButton = { TextButton(onClick = vm::eliminar, modifier = Modifier.testTag("perfilConfirmarEliminar")) { Text(stringResource(R.string.perfil_confirm_delete)) } },
        dismissButton = { TextButton(onClick = vm::cancelarEliminar, modifier = Modifier.testTag("perfilCancelarEliminar")) { Text(stringResource(R.string.perfil_cancel)) } })
}
private fun errorMessage(error: AppError, write: Boolean): Int = when (error) {
    AppError.SolicitudInvalida -> R.string.perfil_invalid
    AppError.SesionNoValida -> if (write) R.string.perfil_session_write else R.string.perfil_session_read
    AppError.AccesoDenegado -> R.string.perfil_forbidden
    AppError.ServicioNoDisponible -> R.string.perfil_unavailable
    AppError.ErrorConexion, AppError.RespuestaInvalida -> R.string.perfil_connection
    is AppError.ErrorHttp -> when (error.codigo) { 409 -> R.string.perfil_conflict; 404 -> R.string.perfil_notfound; else -> R.string.perfil_unknown }
}
