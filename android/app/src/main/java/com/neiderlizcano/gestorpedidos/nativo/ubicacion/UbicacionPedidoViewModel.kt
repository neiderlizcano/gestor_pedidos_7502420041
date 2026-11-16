package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Motivos por los que no se pudo completar la dirección. */
enum class ErrorUbicacion { SIN_PROVEEDOR, TIEMPO_AGOTADO, SIN_CONEXION, SERVICIO_NO_DISPONIBLE, SIN_RESULTADOS, FALLO }

sealed interface EstadoUbicacion {
    /** Estado inicial: no se ha pedido nada. Nada se ejecuta hasta que el usuario pulsa el botón. */
    data object Inactivo : EstadoUbicacion
    data object SolicitandoPermiso : EstadoUbicacion
    /** `permanente`: el sistema ya no volverá a mostrar el diálogo; solo se puede conceder desde Ajustes. */
    data class PermisoDenegado(val permanente: Boolean) : EstadoUbicacion
    data object UbicacionDesactivada : EstadoUbicacion
    data object ObteniendoPosicion : EstadoUbicacion
    data object ResolviendoDireccion : EstadoUbicacion
    /** Se entregó una sugerencia al formulario; `avisos`/`faltantes` explican qué revisar. */
    data class Completado(val faltantes: Set<CampoDireccion>, val avisos: Set<AvisoDireccion>) : EstadoUbicacion
    data class Error(val motivo: ErrorUbicacion) : EstadoUbicacion
}

/**
 * Coordina permiso, posición y geocodificación inversa para un solo disparo del usuario. No guarda
 * coordenadas, no hace seguimiento continuo y no se ejecuta por sí mismo.
 *
 * Las sugerencias salen por [sugerencias] como eventos de un solo uso, para que una rotación o una
 * recomposición no vuelvan a sobrescribir lo que el usuario ya editó.
 */
class UbicacionPedidoViewModel(
    private val proveedor: ProveedorPosicion,
    private val geocodificador: GeocodificadorInverso,
    testScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val estado = MutableStateFlow<EstadoUbicacion>(EstadoUbicacion.Inactivo)
    val uiState: StateFlow<EstadoUbicacion> = estado.asStateFlow()

    private val salida = Channel<DireccionSugerida>(Channel.BUFFERED)
    val sugerencias: Flow<DireccionSugerida> = salida.receiveAsFlow()

    private var trabajo: Job? = null
    private var generacion = 0L

    /** Verdadero mientras hay una operación en curso (se usa para ignorar pulsaciones repetidas). */
    val ocupado: Boolean
        get() = trabajo?.isActive == true

    fun permisoSolicitado() {
        if (ocupado) return
        estado.value = EstadoUbicacion.SolicitandoPermiso
    }

    fun permisoDenegado(permanente: Boolean) {
        estado.value = EstadoUbicacion.PermisoDenegado(permanente)
    }

    /** Inicia la búsqueda con el permiso ya concedido. `aproximado`: solo se concedió ubicación aproximada. */
    fun iniciar(aproximado: Boolean) {
        if (trabajo?.isActive == true) return
        val intento = ++generacion
        trabajo = scope.launch {
            try {
                estado.value = EstadoUbicacion.ObteniendoPosicion
                val r = proveedor.obtenerActual(precisa = !aproximado)
                currentCoroutineContext().ensureActive()
                val posicion = when (r) {
                    is ResultadoPosicion.Exito -> r.posicion
                    ResultadoPosicion.UbicacionDesactivada -> { estado.value = EstadoUbicacion.UbicacionDesactivada; return@launch }
                    ResultadoPosicion.PermisoDenegado -> { estado.value = EstadoUbicacion.PermisoDenegado(false); return@launch }
                    ResultadoPosicion.SinProveedor -> { fallo(ErrorUbicacion.SIN_PROVEEDOR); return@launch }
                    ResultadoPosicion.TiempoAgotado -> { fallo(ErrorUbicacion.TIEMPO_AGOTADO); return@launch }
                    ResultadoPosicion.Fallo -> { fallo(ErrorUbicacion.FALLO); return@launch }
                }
                estado.value = EstadoUbicacion.ResolviendoDireccion
                val g = geocodificador.resolver(posicion)
                currentCoroutineContext().ensureActive()
                val cruda = when (g) {
                    is ResultadoGeocodificacion.Exito -> g.direccion
                    ResultadoGeocodificacion.TiempoAgotado -> { fallo(ErrorUbicacion.TIEMPO_AGOTADO); return@launch }
                    ResultadoGeocodificacion.SinResultados -> { fallo(ErrorUbicacion.SIN_RESULTADOS); return@launch }
                    ResultadoGeocodificacion.SinConexion -> { fallo(ErrorUbicacion.SIN_CONEXION); return@launch }
                    ResultadoGeocodificacion.ServicioNoDisponible -> { fallo(ErrorUbicacion.SERVICIO_NO_DISPONIBLE); return@launch }
                    ResultadoGeocodificacion.Fallo -> { fallo(ErrorUbicacion.FALLO); return@launch }
                }
                val sugerida = cruda.aSugerencia(precisionAproximada = aproximado)
                if (sugerida.vacia) { fallo(ErrorUbicacion.SIN_RESULTADOS); return@launch }
                salida.trySend(sugerida)
                estado.value = EstadoUbicacion.Completado(sugerida.faltantes, sugerida.avisos)
            } catch (cancelada: CancellationException) {
                // La cancelación (botón Cancelar o salida de la pantalla) no es un error: no se entrega nada.
                if (intento == generacion &&
                    (estado.value is EstadoUbicacion.ObteniendoPosicion || estado.value is EstadoUbicacion.ResolviendoDireccion)) {
                    estado.value = EstadoUbicacion.Inactivo
                }
                throw cancelada
            } catch (_: RuntimeException) {
                if (intento == generacion) fallo(ErrorUbicacion.FALLO)
            }
        }
    }

    /** Cancela la búsqueda en curso sin modificar el formulario. */
    fun cancelar() {
        generacion++
        trabajo?.cancel()
        trabajo = null
        if (estado.value !is EstadoUbicacion.Completado) estado.value = EstadoUbicacion.Inactivo
    }

    /**
     * Se llama al salir de la pantalla: cancela lo que esté en curso y descarta una sugerencia pendiente para que
     * nunca se aplique después a otro formulario.
     */
    fun descartar() {
        generacion++
        trabajo?.cancel()
        trabajo = null
        while (salida.tryReceive().isSuccess) { /* vaciar */ }
        estado.value = EstadoUbicacion.Inactivo
    }

    /** Oculta el último mensaje (por ejemplo, al editar manualmente los campos). */
    fun descartarMensaje() {
        if (!ocupado) estado.value = EstadoUbicacion.Inactivo
    }

    private fun fallo(motivo: ErrorUbicacion) { estado.value = EstadoUbicacion.Error(motivo) }
}
