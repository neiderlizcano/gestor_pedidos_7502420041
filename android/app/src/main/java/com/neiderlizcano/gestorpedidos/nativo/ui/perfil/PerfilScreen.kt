package com.neiderlizcano.gestorpedidos.nativo.ui.perfil

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import com.neiderlizcano.gestorpedidos.nativo.ui.components.BotonApp
import com.neiderlizcano.gestorpedidos.nativo.ui.components.EstiloBoton
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
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
        // Cabecera con avatar de iniciales, como en la maqueta; sin perfil cargado se muestra solo el título.
        val perfilActual = state.perfil
        if (perfilActual == null) Text(stringResource(R.string.perfil_title), style = MaterialTheme.typography.headlineMedium)
        else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Text(iniciales(perfilActual.nombre), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column { Text(perfilActual.nombre, style = MaterialTheme.typography.titleMedium); Text(perfilActual.username, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("perfilProgress"))
        state.error?.let { error ->
            Text(stringResource(errorMessage(error, state.writeFailure)), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("perfilError"))
        }
        if (state.guardado) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.perfil_saved), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(16.dp).testTag("perfilExito"))
        }
        if (state.uncertain != null) Text(stringResource(if (state.uncertain == EscrituraPerfil.ELIMINAR)
            R.string.perfil_delete_uncertain else R.string.perfil_write_uncertain), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("perfilUncertain"))
        if (state.deleted) Text(stringResource(R.string.perfil_deleted)) else {
            BotonApp(stringResource(R.string.perfil_refresh), vm::consultar, Modifier.fillMaxWidth().testTag("perfilConsultar"), EstiloBoton.SECUNDARIO,
                Icons.Filled.Refresh, enabled = !state.busy)
            if (state.editing) {
                OutlinedTextField(state.username, vm::cambiarUsername, enabled = !state.busy, singleLine = true,
                    label = { Text(stringResource(R.string.perfil_username)) }, isError = state.usernameRequired, colors = com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.colorCampo(),
                    supportingText = { if (state.usernameRequired) Text(stringResource(R.string.perfil_required)) },
                    modifier = Modifier.fillMaxWidth().testTag("perfilUsername"))
                OutlinedTextField(state.nombre, vm::cambiarNombre, enabled = !state.busy, singleLine = true,
                    label = { Text(stringResource(R.string.perfil_nombre)) }, isError = state.nombreRequired, colors = com.neiderlizcano.gestorpedidos.nativo.ui.pedidos.colorCampo(),
                    supportingText = { if (state.nombreRequired) Text(stringResource(R.string.perfil_required)) },
                    modifier = Modifier.fillMaxWidth().testTag("perfilNombre"))
                Text(stringResource(R.string.perfil_email_readonly))
                Text(state.perfil?.email ?: stringResource(R.string.perfil_email_auth), modifier = Modifier.testTag("perfilCorreo"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BotonApp(stringResource(R.string.perfil_cancel), vm::cancelar, Modifier.weight(1f).testTag("perfilCancelar"), EstiloBoton.SECUNDARIO,
                        Icons.Filled.Close, enabled = !state.busy)
                    BotonApp(stringResource(R.string.perfil_save), vm::guardar, Modifier.weight(1f).testTag("perfilGuardar"), icono = Icons.Filled.Check,
                        enabled = !state.busy && state.uncertain == null, cargando = state.busy)
                }
            } else {
                if (state.missing) {
                    Text(stringResource(R.string.perfil_missing), modifier = Modifier.testTag("perfilMissing"))
                    BotonApp(stringResource(R.string.perfil_create), vm::crear, Modifier.fillMaxWidth().testTag("perfilCrear"), icono = Icons.Filled.Add,
                        enabled = !state.busy && state.uncertain == null)
                }
                state.perfil?.let { perfil ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column { Text(stringResource(R.string.perfil_username), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(perfil.username, modifier = Modifier.testTag("perfilUsernameValue")) }
                            Column { Text(stringResource(R.string.perfil_nombre), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(perfil.nombre, modifier = Modifier.testTag("perfilNombreValue")) }
                            Column { Text(stringResource(R.string.perfil_email_readonly), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(perfil.email, modifier = Modifier.testTag("perfilCorreo")) }
                        }
                    }
                }
                // Editar es la acción principal; eliminar la cuenta queda diferenciado en rojo y pide confirmación.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.perfil != null) BotonApp(stringResource(R.string.perfil_edit), vm::editar, Modifier.weight(1f).testTag("perfilEditar"),
                        icono = Icons.Filled.Edit, enabled = !state.busy && state.uncertain == null)
                    if (state.missing || state.perfil != null) BotonApp(stringResource(R.string.perfil_delete), vm::pedirEliminar,
                        Modifier.weight(1f).testTag("perfilEliminar"), EstiloBoton.PELIGRO, Icons.Filled.Delete, enabled = !state.busy && state.uncertain == null)
                }
            }
        }
    }
    if (state.confirmDelete) AlertDialog(onDismissRequest = vm::cancelarEliminar,
        title = { Text(stringResource(R.string.perfil_delete)) }, text = { Text(stringResource(R.string.perfil_delete_warning)) },
        confirmButton = { BotonApp(stringResource(R.string.perfil_confirm_delete), vm::eliminar, Modifier.testTag("perfilConfirmarEliminar"), EstiloBoton.PELIGRO, Icons.Filled.Delete) },
        dismissButton = { BotonApp(stringResource(R.string.perfil_cancel), vm::cancelarEliminar, Modifier.testTag("perfilCancelarEliminar"), EstiloBoton.SECUNDARIO) })
}
private fun iniciales(nombre: String): String =
    nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }

private fun errorMessage(error: AppError, write: Boolean): Int = when (error) {
    AppError.SolicitudInvalida -> R.string.perfil_invalid
    AppError.SesionNoValida -> if (write) R.string.perfil_session_write else R.string.perfil_session_read
    AppError.AccesoDenegado -> R.string.perfil_forbidden
    AppError.ServicioNoDisponible -> R.string.perfil_unavailable
    AppError.ErrorConexion, AppError.RespuestaInvalida -> R.string.perfil_connection
    is AppError.ErrorHttp -> when (error.codigo) { 409 -> R.string.perfil_conflict; 404 -> R.string.perfil_notfound; else -> R.string.perfil_unknown }
}
