package com.neiderlizcano.gestorpedidos.nativo.sensores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import java.util.Locale

/**
 * Pantalla del módulo de sensores con el hardware real del dispositivo.
 *
 * Integración: SessionDashboard invoca esta función desde el
 * destino que se decida, dentro del propietario de ViewModels de la sesión si la pantalla debe
 * descartarse al cerrar sesión. El módulo no usa red, tokens ni datos del usuario y no requiere permisos
 * en el manifiesto (el acelerómetro y la proximidad no los necesitan a la frecuencia usada).
 */
@Composable
fun SensoresRoute() {
    val contexto = LocalContext.current
    val hardware = remember(contexto) { AndroidSensorHardware.desde(contexto) }
    SensoresRoute(hardware)
}

/** Variante con hardware inyectado, usada por las pruebas y por quien necesite un doble. */
@Composable
fun SensoresRoute(hardware: SensorHardware) {
    val fuente = remember(hardware) { FuenteSensores(hardware) }
    val acelerometro: AcelerometroViewModel = viewModel(
        factory = fabrica(AcelerometroViewModel::class.java) { AcelerometroViewModel(fuente) })
    val proximidad: ProximidadViewModel = viewModel(
        factory = fabrica(ProximidadViewModel::class.java) { ProximidadViewModel(fuente) })
    // La recolección respeta el ciclo de vida: se detiene en onStop y libera el sensor.
    val accUi by acelerometro.uiState.collectAsStateWithLifecycle()
    val proxUi by proximidad.uiState.collectAsStateWithLifecycle()
    SensoresScreen(accUi, proxUi, acelerometro::reintentar, proximidad::reintentar)
}

@Composable
internal fun SensoresScreen(acc: AcelerometroUi, prox: ProximidadUi, onReintentarAcc: () -> Unit, onReintentarProx: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp).testTag("sensoresPantalla"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.sensores_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.sensores_description))
        PanelAcelerometro(acc, onReintentarAcc)
        PanelProximidad(prox, onReintentarProx)
    }
}

@Composable
private fun PanelSensor(titulo: Int, tag: String, contenido: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag(tag), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.titleLarge)
            contenido()
        }
    }
}

@Composable
private fun MensajeEstado(texto: Int, tag: String, error: Boolean = false) {
    Text(
        stringResource(texto), modifier = Modifier.testTag(tag),
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EsperaConIndicador(texto: Int, tag: String) {
    CircularProgressIndicator(Modifier.testTag(tag + "Progreso"))
    MensajeEstado(texto, tag)
}

@Composable
private fun MensajeError(motivo: MotivoErrorSensor, tag: String, onReintentar: () -> Unit) {
    MensajeEstado(
        if (motivo == MotivoErrorSensor.REGISTRO_RECHAZADO) R.string.sensores_error_register else R.string.sensores_error_unexpected,
        tag, error = true
    )
    OutlinedButton(onClick = onReintentar, modifier = Modifier.testTag(tag + "Reintentar")) { Text(stringResource(R.string.sensores_retry)) }
}

@Composable
internal fun PanelAcelerometro(estado: AcelerometroUi, onReintentar: () -> Unit) {
    PanelSensor(R.string.sensores_acc_title, "sensorAcelerometro") {
        when (estado) {
            AcelerometroUi.Esperando -> EsperaConIndicador(R.string.sensores_acc_waiting, "accEstado")
            AcelerometroUi.NoDisponible -> MensajeEstado(R.string.sensores_acc_unavailable, "accEstado")
            AcelerometroUi.DatosNoDisponibles -> MensajeEstado(R.string.sensores_acc_invalid, "accEstado")
            is AcelerometroUi.Error -> MensajeError(estado.motivo, "accEstado", onReintentar)
            is AcelerometroUi.Activo -> {
                Text(stringResource(R.string.sensores_acc_axis, "X", formato(estado.x)), modifier = Modifier.testTag("accX"))
                Text(stringResource(R.string.sensores_acc_axis, "Y", formato(estado.y)), modifier = Modifier.testTag("accY"))
                Text(stringResource(R.string.sensores_acc_axis, "Z", formato(estado.z)), modifier = Modifier.testTag("accZ"))
                Text(stringResource(R.string.sensores_acc_magnitude, formato(estado.magnitud), formato(DetectorMovimiento.GRAVEDAD_MS2)), modifier = Modifier.testTag("accMagnitud"))
                Text(
                    stringResource(if (estado.movimiento) R.string.sensores_acc_moving else R.string.sensores_acc_rest),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (estado.movimiento) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(if (estado.movimiento) "accMovimiento" else "accReposo")
                )
                Text(stringResource(R.string.sensores_acc_count, estado.detecciones), modifier = Modifier.testTag("accConteo"))
                if (estado.precisionBaja) MensajeEstado(R.string.sensores_acc_low_accuracy, "accPrecision")
            }
        }
    }
}

@Composable
internal fun PanelProximidad(estado: ProximidadUi, onReintentar: () -> Unit) {
    PanelSensor(R.string.sensores_prox_title, "sensorProximidad") {
        when (estado) {
            ProximidadUi.Esperando -> EsperaConIndicador(R.string.sensores_prox_waiting, "proxEstado")
            ProximidadUi.NoDisponible -> MensajeEstado(R.string.sensores_prox_unavailable, "proxEstado")
            ProximidadUi.DatosNoDisponibles -> MensajeEstado(R.string.sensores_prox_invalid, "proxEstado")
            is ProximidadUi.Error -> MensajeError(estado.motivo, "proxEstado", onReintentar)
            is ProximidadUi.Medida -> {
                Text(
                    stringResource(if (estado.cerca) R.string.sensores_prox_near else R.string.sensores_prox_far),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (estado.cerca) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(if (estado.cerca) "proxCerca" else "proxLejos")
                )
                // Los sensores binarios no miden distancia real: solo se muestra la capacidad que tienen.
                if (estado.binario) Text(stringResource(R.string.sensores_prox_binary, formato(estado.rangoMaximoCm)), modifier = Modifier.testTag("proxBinario"))
                else {
                    Text(stringResource(R.string.sensores_prox_distance, formato(estado.distanciaCm)), modifier = Modifier.testTag("proxDistancia"))
                    Text(stringResource(R.string.sensores_prox_range, formato(estado.rangoMaximoCm)))
                }
            }
        }
    }
}

private fun formato(valor: Float): String = String.format(Locale.getDefault(), "%.2f", valor)

private fun <VM : ViewModel> fabrica(clase: Class<VM>, crear: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == clase)
            @Suppress("UNCHECKED_CAST")
            return crear() as T
        }
    }
