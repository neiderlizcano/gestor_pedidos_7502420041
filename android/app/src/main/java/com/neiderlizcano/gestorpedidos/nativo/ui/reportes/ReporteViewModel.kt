package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroEstadoFechas
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroNombreUsername
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroUbicacion
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PaginaReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PerfilReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.SolicitudPagina
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado observable de un reporte. `form` conserva lo escrito por el usuario (sobrevive a rotaciones
 * porque vive en el ViewModel) y `datos` solo contiene el resultado de la última consulta aplicada.
 */
data class ReporteUi<Form, Datos>(
    val form: Form,
    val busy: Boolean = false,
    /** Verdadero cuando la última consulta terminó con éxito, aunque no haya filas. */
    val buscado: Boolean = false,
    val error: AppError? = null,
    val problemas: Map<ReporteCampo, ReporteProblema> = emptyMap(),
    val datos: Datos? = null
)

/**
 * Lógica común de los cuatro reportes: validar, consultar, paginar y reintentar. Cada reporte conserva
 * su propio ViewModel, formulario y pantalla. No recibe identificadores de usuario: la identidad la
 * determina únicamente el token de la sesión que usa el repositorio inyectado.
 */
abstract class ReporteViewModel<Form : Any, F : Any, D : Any>(
    private val formularioInicial: Form,
    testScope: CoroutineScope?
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val state = MutableStateFlow(ReporteUi<Form, D>(formularioInicial))
    val uiState: StateFlow<ReporteUi<Form, D>> = state.asStateFlow()

    // Instantánea de lo que realmente se consultó: la paginación no depende de lo que se esté editando.
    private var aplicado: F? = null
    private var tamanoAplicado = TAMANO_PAGINA_PREDETERMINADO
    private var ultimaPagina = 0

    protected abstract fun validar(form: Form): ValidacionFiltro<F>
    protected abstract suspend fun ejecutar(filtro: F, pagina: SolicitudPagina): AppResult<D>
    protected open fun tamanoDe(form: Form): Int = TAMANO_PAGINA_PREDETERMINADO
    protected open fun paginaDe(datos: D): PaginaReporte<*>? = null

    /** Modifica el formulario; se ignora mientras hay una consulta en curso. */
    fun cambiar(transformar: (Form) -> Form) {
        if (state.value.busy) return
        state.update { it.copy(form = transformar(it.form), problemas = emptyMap()) }
    }

    /** Valida y ejecuta una consulta nueva desde la primera página. */
    fun consultar() {
        if (state.value.busy) return
        when (val validacion = validar(state.value.form)) {
            is ValidacionFiltro.Invalido -> state.update { it.copy(problemas = validacion.problemas, error = null) }
            is ValidacionFiltro.Valido -> {
                aplicado = validacion.filtro
                tamanoAplicado = tamanoDe(state.value.form)
                lanzar(0, consultaNueva = true)
            }
        }
    }

    fun paginaSiguiente() {
        val pagina = state.value.datos?.let { paginaDe(it) } ?: return
        if (pagina.haySiguiente) irA(pagina.pagina + 1)
    }

    fun paginaAnterior() {
        val pagina = state.value.datos?.let { paginaDe(it) } ?: return
        if (pagina.pagina > 0) irA(pagina.pagina - 1)
    }

    /** Repite la última petición (solo lecturas, por lo que es seguro) tras un fallo. */
    fun reintentar() {
        if (state.value.busy || aplicado == null) return
        lanzar(ultimaPagina, consultaNueva = state.value.datos == null)
    }

    /** Descarta filtros y resultados. */
    fun limpiar() {
        if (state.value.busy) return
        aplicado = null
        ultimaPagina = 0
        state.value = ReporteUi(formularioInicial)
    }

    private fun irA(pagina: Int) {
        if (state.value.busy || aplicado == null) return
        lanzar(pagina, consultaNueva = false)
    }

    private fun lanzar(pagina: Int, consultaNueva: Boolean) {
        val filtro = aplicado ?: return
        val tamano = tamanoAplicado
        ultimaPagina = pagina
        state.update {
            // Una consulta nueva no debe mostrar filas de otro filtro mientras carga ni si falla.
            it.copy(busy = true, error = null, problemas = emptyMap(),
                datos = if (consultaNueva) null else it.datos, buscado = if (consultaNueva) false else it.buscado)
        }
        scope.launch {
            try {
                when (val resultado = ejecutar(filtro, SolicitudPagina(pagina, tamano))) {
                    is AppResult.Exito -> state.update { it.copy(datos = resultado.valor, buscado = true) }
                    is AppResult.Fallo -> state.update { it.copy(error = resultado.error) }
                }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }
}

/** Reporte de pedidos por estado y rango de fechas (paginado). */
class PedidosEstadoFechasViewModel(
    private val repositorio: ReportesRepository,
    testScope: CoroutineScope? = null
) : ReporteViewModel<FormularioEstadoFechas, FiltroEstadoFechas, PaginaReporte<PedidoReporte>>(FormularioEstadoFechas(), testScope) {
    override fun validar(form: FormularioEstadoFechas) = validarEstadoFechas(form)
    override suspend fun ejecutar(filtro: FiltroEstadoFechas, pagina: SolicitudPagina) =
        repositorio.pedidosPorEstadoFechas(filtro, pagina)
    override fun tamanoDe(form: FormularioEstadoFechas) = form.tamano
    override fun paginaDe(datos: PaginaReporte<PedidoReporte>) = datos
}

/** Reporte de pedidos por país, departamento y ciudad (paginado). */
class PedidosUbicacionViewModel(
    private val repositorio: ReportesRepository,
    testScope: CoroutineScope? = null
) : ReporteViewModel<FormularioUbicacion, FiltroUbicacion, PaginaReporte<PedidoReporte>>(FormularioUbicacion(), testScope) {
    override fun validar(form: FormularioUbicacion) = validarUbicacion(form)
    override suspend fun ejecutar(filtro: FiltroUbicacion, pagina: SolicitudPagina) =
        repositorio.pedidosPorUbicacion(filtro, pagina)
    override fun tamanoDe(form: FormularioUbicacion) = form.tamano
    override fun paginaDe(datos: PaginaReporte<PedidoReporte>) = datos
}

/** Reporte de perfiles por nombre o username; el servidor solo devuelve el perfil de la sesión. */
class PerfilesNombreUsernameViewModel(
    private val repositorio: ReportesRepository,
    testScope: CoroutineScope? = null
) : ReporteViewModel<FormularioNombreUsername, FiltroNombreUsername, List<PerfilReporte>>(FormularioNombreUsername(), testScope) {
    override fun validar(form: FormularioNombreUsername) = validarNombreUsername(form)
    override suspend fun ejecutar(filtro: FiltroNombreUsername, pagina: SolicitudPagina) =
        repositorio.perfilesPorNombreUsername(filtro)
}

/** Reporte de perfiles por correo. */
class PerfilesCorreoViewModel(
    private val repositorio: ReportesRepository,
    testScope: CoroutineScope? = null
) : ReporteViewModel<FormularioCorreo, String, List<PerfilReporte>>(FormularioCorreo(), testScope) {
    override fun validar(form: FormularioCorreo) = validarCorreo(form)
    override suspend fun ejecutar(filtro: String, pagina: SolicitudPagina) = repositorio.perfilesPorCorreo(filtro)
}
