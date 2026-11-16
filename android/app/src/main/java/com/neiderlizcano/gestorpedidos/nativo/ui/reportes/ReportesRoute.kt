package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository

/** Reportes disponibles; el nombre se guarda como texto para sobrevivir a la recreación de la Activity. */
enum class ReporteTipo { PEDIDOS_ESTADO_FECHAS, PEDIDOS_UBICACION, USUARIOS_NOMBRE_USERNAME, USUARIOS_CORREO }

/**
 * Punto de entrada del módulo de reportes: menú con los cuatro reportes y navegación interna propia.
 * No modifica la navegación compartida de la aplicación.
 *
 * Se muestra desde el menú de la sesión, dentro del propietario de ViewModels de la cuenta, por lo que los
 * ViewModels de reportes se descartan al cerrar sesión o cambiar de cuenta. Recibe un [ReportesRepository] que usa
 * el mismo proveedor de token que el CRUD (`AppContainer.reportesRepository`); este módulo nunca recibe tokens ni
 * identificadores de usuario.
 */
@Composable
fun ReportesRoute(repositorio: ReportesRepository) {
    var seleccionado by rememberSaveable { mutableStateOf<String?>(null) }
    val tipo = seleccionado?.let { nombre -> ReporteTipo.entries.firstOrNull { it.name == nombre } }
    BackHandler(enabled = tipo != null) { seleccionado = null }
    if (tipo == null) {
        ReportesMenu { seleccionado = it.name }
    } else {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            TextButton(onClick = { seleccionado = null }, modifier = Modifier.padding(horizontal = 12.dp).testTag("reportesVolver")) {
                Text(stringResource(R.string.reportes_back))
            }
            when (tipo) {
                ReporteTipo.PEDIDOS_ESTADO_FECHAS -> PedidosEstadoFechasRoute(repositorio)
                ReporteTipo.PEDIDOS_UBICACION -> PedidosUbicacionRoute(repositorio)
                ReporteTipo.USUARIOS_NOMBRE_USERNAME -> PerfilesNombreUsernameRoute(repositorio)
                ReporteTipo.USUARIOS_CORREO -> PerfilesCorreoRoute(repositorio)
            }
        }
    }
}

@Composable
internal fun ReportesMenu(onSeleccion: (ReporteTipo) -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.reportes_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.reportes_menu_description))
        ReporteTipo.entries.forEach { tipo ->
            Button(onClick = { onSeleccion(tipo) }, modifier = Modifier.fillMaxWidth().testTag("reporteMenu_" + tipo.name)) {
                Text(stringResource(when (tipo) {
                    ReporteTipo.PEDIDOS_ESTADO_FECHAS -> R.string.reportes_menu_pedidos_estado
                    ReporteTipo.PEDIDOS_UBICACION -> R.string.reportes_menu_pedidos_ubicacion
                    ReporteTipo.USUARIOS_NOMBRE_USERNAME -> R.string.reportes_menu_usuarios_nombre
                    ReporteTipo.USUARIOS_CORREO -> R.string.reportes_menu_usuarios_correo
                }))
            }
        }
    }
}

