package com.neiderlizcano.gestorpedidos.nativo.sensores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

// Los ViewModels solo exponen estado. La suscripción al sensor existe únicamente mientras la interfaz
// recolecta `uiState` (con `collectAsStateWithLifecycle`, es decir, entre onStart y onStop) porque el
// estado se comparte con `WhileSubscribed(0)`: al pasar la pantalla a segundo plano el sensor se libera.

sealed interface AcelerometroUi {
    /** Suscrito, pero aún sin ninguna lectura. */
    data object Esperando : AcelerometroUi
    data object NoDisponible : AcelerometroUi
    data object DatosNoDisponibles : AcelerometroUi
    data class Error(val motivo: MotivoErrorSensor) : AcelerometroUi
    data class Activo(
        val x: Float, val y: Float, val z: Float, val magnitud: Float,
        /** Verdadero durante [DURACION_AVISO_NS] tras la última sacudida significativa. */
        val movimiento: Boolean,
        val detecciones: Int,
        val precisionBaja: Boolean
    ) : AcelerometroUi
}

sealed interface ProximidadUi {
    data object Esperando : ProximidadUi
    data object NoDisponible : ProximidadUi
    data object DatosNoDisponibles : ProximidadUi
    data class Error(val motivo: MotivoErrorSensor) : ProximidadUi
    data class Medida(val cerca: Boolean, val distanciaCm: Float, val rangoMaximoCm: Float, val binario: Boolean) : ProximidadUi
}

/** Tiempo que se mantiene visible el aviso de movimiento tras una sacudida. */
const val DURACION_AVISO_NS = 1_500_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class AcelerometroViewModel(
    private val fuente: FuenteSensores,
    testScope: CoroutineScope? = null,
    private val crearDetector: () -> DetectorMovimiento = { DetectorMovimiento() }
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val intento = MutableStateFlow(0)

    val uiState: StateFlow<AcelerometroUi> = intento
        .flatMapLatest { observar() }
        .stateIn(scope, SharingStarted.WhileSubscribed(0), AcelerometroUi.Esperando)

    /** Vuelve a suscribirse tras un error de registro. */
    fun reintentar() { intento.update { it + 1 } }

    private fun observar(): Flow<AcelerometroUi> = flow {
        // El estado del detector pertenece a cada recolección: al reanudar la pantalla empieza de cero.
        val detector = crearDetector()
        var detecciones = 0
        var ultimoMovimientoNs: Long? = null
        emit(AcelerometroUi.Esperando)
        emitAll(fuente.acelerometro().map { evento ->
            when (evento) {
                EventoSensor.NoDisponible -> AcelerometroUi.NoDisponible
                EventoSensor.DatosNoDisponibles -> AcelerometroUi.DatosNoDisponibles
                is EventoSensor.Error -> AcelerometroUi.Error(evento.motivo)
                is EventoSensor.Lectura -> {
                    val lectura = evento.valor
                    val resultado = detector.procesar(lectura)
                    if (resultado.deteccionNueva) detecciones++
                    if (resultado.movimiento) ultimoMovimientoNs = lectura.instanteNs
                    val reciente = ultimoMovimientoNs?.let { lectura.instanteNs - it < DURACION_AVISO_NS } ?: false
                    AcelerometroUi.Activo(lectura.x, lectura.y, lectura.z, lectura.magnitud(), reciente, detecciones, lectura.precisionBaja)
                }
            }
        })
    }.catch { emit(AcelerometroUi.Error(MotivoErrorSensor.FALLO_INESPERADO)) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProximidadViewModel(
    private val fuente: FuenteSensores,
    testScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val intento = MutableStateFlow(0)

    val uiState: StateFlow<ProximidadUi> = intento
        .flatMapLatest { observar() }
        .stateIn(scope, SharingStarted.WhileSubscribed(0), ProximidadUi.Esperando)

    fun reintentar() { intento.update { it + 1 } }

    private fun observar(): Flow<ProximidadUi> = flow {
        emit(ProximidadUi.Esperando)
        emitAll(fuente.proximidad().map { evento ->
            when (evento) {
                EventoSensor.NoDisponible -> ProximidadUi.NoDisponible
                EventoSensor.DatosNoDisponibles -> ProximidadUi.DatosNoDisponibles
                is EventoSensor.Error -> ProximidadUi.Error(evento.motivo)
                is EventoSensor.Lectura -> with(evento.valor) { ProximidadUi.Medida(cerca, distanciaCm, rangoMaximoCm, esBinario) }
            }
        })
    }.catch { emit(ProximidadUi.Error(MotivoErrorSensor.FALLO_INESPERADO)) }
}
