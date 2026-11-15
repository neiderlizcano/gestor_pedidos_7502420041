package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R

/** Consulta de permisos de ubicación; se abstrae para poder probar cada caso sin tocar el sistema. */
interface PermisosUbicacion {
    fun preciso(): Boolean
    fun aproximado(): Boolean
    /** Tras una denegación: el sistema ya no mostrará el diálogo y solo Ajustes puede conceder el permiso. */
    fun denegacionPermanente(): Boolean
}

private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

private class PermisosDelSistema(private val contexto: Context) : PermisosUbicacion {
    private fun concedido(permiso: String) = ContextCompat.checkSelfPermission(contexto, permiso) == PackageManager.PERMISSION_GRANTED
    override fun preciso() = concedido(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun aproximado() = concedido(Manifest.permission.ACCESS_COARSE_LOCATION)
    override fun denegacionPermanente(): Boolean {
        val actividad = contexto.actividad() ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(actividad, Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

/**
 * Botón «Usar mi ubicación» reutilizable para crear y actualizar pedidos.
 *
 * Solo actúa cuando el usuario lo pulsa: entonces pide el permiso (si hace falta), obtiene UNA posición,
 * la convierte en dirección con el geocodificador del sistema y llama a [onDireccion] con los campos que
 * se pudieron obtener (los demás llegan como null y el formulario debe conservar lo que ya tenía el usuario).
 * No hay seguimiento en segundo plano ni se guardan coordenadas.
 *
 * Integración: ver `docs/UBICACION.md`. Debe vivir en el propietario de ViewModels de la pantalla de pedidos.
 */
@Composable
fun UsarMiUbicacion(onDireccion: (DireccionSugerida) -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val fabrica = remember(contexto) {
        fabricaUbicacion {
            val app = contexto.applicationContext
            UbicacionPedidoViewModel(ProveedorPosicionAndroid(app), GeocodificadorAndroid(app))
        }
    }
    val vm: UbicacionPedidoViewModel = viewModel(factory = fabrica)
    UsarMiUbicacion(vm, remember(contexto) { PermisosDelSistema(contexto) }, onDireccion, modifier)
}

/** Variante con dependencias inyectadas (pruebas y pantallas que ya poseen el ViewModel). */
@Composable
fun UsarMiUbicacion(
    vm: UbicacionPedidoViewModel,
    permisos: PermisosUbicacion,
    onDireccion: (DireccionSugerida) -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val alRecibir by rememberUpdatedState(onDireccion)
    // La sugerencia es un evento de un solo uso: no se vuelve a aplicar al rotar ni al recomponer.
    LaunchedEffect(vm) { vm.sugerencias.collect { alRecibir(it) } }
    // Al salir de la composición (cerrar el editor) no queda nada pendiente que pueda aplicarse a otro pedido.
    DisposableEffect(vm) { onDispose { vm.descartar() } }

    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultado ->
        val preciso = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val aproximado = resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (preciso || aproximado) vm.iniciar(aproximado = !preciso)
        else vm.permisoDenegado(permanente = permisos.denegacionPermanente())
    }

    PanelUbicacion(
        estado = estado,
        onUsar = {
            if (vm.ocupado) return@PanelUbicacion
            if (permisos.preciso() || permisos.aproximado()) vm.iniciar(aproximado = !permisos.preciso())
            else {
                vm.permisoSolicitado()
                lanzador.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        },
        onCancelar = vm::cancelar,
        onAjustesAplicacion = {
            contexto.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        onAjustesUbicacion = {
            contexto.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        modifier = modifier
    )
}

/** Parte visual sin estado ni lógica: solo dibuja el estado recibido. */
@Composable
internal fun PanelUbicacion(
    estado: EstadoUbicacion,
    onUsar: () -> Unit,
    onCancelar: () -> Unit,
    onAjustesAplicacion: () -> Unit,
    onAjustesUbicacion: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enCurso = estado is EstadoUbicacion.ObteniendoPosicion || estado is EstadoUbicacion.ResolviendoDireccion
    Column(modifier.fillMaxWidth().testTag("ubicacionPanel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onUsar, enabled = !enCurso, modifier = Modifier.fillMaxWidth().testTag("ubicacionUsar")) {
            Text(stringResource(R.string.ubicacion_usar))
        }
        if (enCurso) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("ubicacionProgreso"))
            Button(onClick = onCancelar, modifier = Modifier.testTag("ubicacionCancelar")) { Text(stringResource(R.string.ubicacion_cancelar)) }
        }
        Mensajes(estado)
        if (estado is EstadoUbicacion.PermisoDenegado && estado.permanente) {
            OutlinedButton(onClick = onAjustesAplicacion, modifier = Modifier.testTag("ubicacionAjustesApp")) {
                Text(stringResource(R.string.ubicacion_abrir_ajustes))
            }
        }
        if (estado is EstadoUbicacion.UbicacionDesactivada) {
            OutlinedButton(onClick = onAjustesUbicacion, modifier = Modifier.testTag("ubicacionAjustesUbicacion")) {
                Text(stringResource(R.string.ubicacion_abrir_ajustes_ubicacion))
            }
        }
    }
}

@Composable
private fun Mensajes(estado: EstadoUbicacion) {
    val principal = when (estado) {
        EstadoUbicacion.Inactivo -> R.string.ubicacion_hint
        EstadoUbicacion.SolicitandoPermiso -> R.string.ubicacion_solicitando
        is EstadoUbicacion.PermisoDenegado -> if (estado.permanente) R.string.ubicacion_permiso_bloqueado else R.string.ubicacion_permiso_denegado
        EstadoUbicacion.UbicacionDesactivada -> R.string.ubicacion_desactivada
        EstadoUbicacion.ObteniendoPosicion -> R.string.ubicacion_obteniendo
        EstadoUbicacion.ResolviendoDireccion -> R.string.ubicacion_resolviendo
        is EstadoUbicacion.Completado -> R.string.ubicacion_completado
        is EstadoUbicacion.Error -> when (estado.motivo) {
            ErrorUbicacion.SIN_PROVEEDOR -> R.string.ubicacion_error_proveedor
            ErrorUbicacion.TIEMPO_AGOTADO -> R.string.ubicacion_error_tiempo
            ErrorUbicacion.SIN_CONEXION -> R.string.ubicacion_error_conexion
            ErrorUbicacion.SERVICIO_NO_DISPONIBLE -> R.string.ubicacion_error_servicio
            ErrorUbicacion.SIN_RESULTADOS -> R.string.ubicacion_error_sin_resultados
            ErrorUbicacion.FALLO -> R.string.ubicacion_error_fallo
        }
    }
    val esError = estado is EstadoUbicacion.Error || estado is EstadoUbicacion.PermisoDenegado || estado is EstadoUbicacion.UbicacionDesactivada
    Text(
        stringResource(principal),
        style = MaterialTheme.typography.bodyMedium,
        color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("ubicacionMensaje")
    )
    if (estado is EstadoUbicacion.Completado) {
        if (estado.faltantes.isNotEmpty()) {
            // `map` es inline, por lo que puede llamar a stringResource; `joinToString` con lambda no.
            val nombres = estado.faltantes.map { stringResource(nombreCampo(it)) }.joinToString(", ")
            Text(stringResource(R.string.ubicacion_no_obtenido, nombres), modifier = Modifier.testTag("ubicacionFaltantes"))
        }
        estado.avisos.forEach { aviso ->
            Text(stringResource(textoAviso(aviso)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ubicacionAviso_" + aviso.name))
        }
    }
}

private fun nombreCampo(campo: CampoDireccion): Int = when (campo) {
    CampoDireccion.PAIS -> R.string.ubicacion_campo_pais
    CampoDireccion.DEPARTAMENTO -> R.string.ubicacion_campo_departamento
    CampoDireccion.CIUDAD -> R.string.ubicacion_campo_ciudad
    CampoDireccion.NOMENCLATURA -> R.string.ubicacion_campo_nomenclatura
}

private fun textoAviso(aviso: AvisoDireccion): Int = when (aviso) {
    AvisoDireccion.CIUDAD_APROXIMADA -> R.string.ubicacion_aviso_ciudad
    AvisoDireccion.SIN_NUMERO -> R.string.ubicacion_aviso_sin_numero
    AvisoDireccion.NUMERO_SIN_CALLE -> R.string.ubicacion_aviso_numero_sin_calle
    AvisoDireccion.PRECISION_APROXIMADA -> R.string.ubicacion_aviso_aproximada
}

internal fun fabricaUbicacion(crear: () -> UbicacionPedidoViewModel): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == UbicacionPedidoViewModel::class.java)
        @Suppress("UNCHECKED_CAST")
        return crear() as T
    }
}
