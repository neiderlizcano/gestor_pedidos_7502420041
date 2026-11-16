package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FechaBase
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PaginaReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository

/**
 * Pantalla del reporte de pedidos por estado y fechas. El ViewModel se obtiene del propietario de
 * ViewModels vigente (en la integración, el almacén privado de la sesión) para no cruzar cuentas.
 */
@Composable
fun PedidosEstadoFechasRoute(repositorio: ReportesRepository) {
    val factory = remember(repositorio) {
        reporteFactory(PedidosEstadoFechasViewModel::class.java) { PedidosEstadoFechasViewModel(repositorio) }
    }
    val vm: PedidosEstadoFechasViewModel = viewModel(factory = factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    PedidosEstadoFechasScreen(ui, vm)
}

@Composable
internal fun PedidosEstadoFechasScreen(ui: ReporteUi<FormularioEstadoFechas, PaginaReporte<PedidoReporte>>, vm: PedidosEstadoFechasViewModel) {
    ReporteLayout(
        titulo = R.string.reportes_menu_pedidos_estado, ui = ui,
        onConsultar = vm::consultar, onLimpiar = vm::limpiar, onReintentar = vm::reintentar,
        vacio = { it.contenido.isEmpty() },
        filtros = {
            val libre = !ui.busy
            CampoEstadoFiltro(ui.form.estado, { v -> vm.cambiar { it.copy(estado = v) } }, libre, ui.problemas[ReporteCampo.ESTADO])
            CampoFechaFiltro(ui.form.desde, { v -> vm.cambiar { it.copy(desde = v) } }, R.string.reportes_field_desde,
                "reporteCampo_desde", libre, ui.problemas[ReporteCampo.DESDE])
            CampoFechaFiltro(ui.form.hasta, { v -> vm.cambiar { it.copy(hasta = v) } }, R.string.reportes_field_hasta,
                "reporteCampo_hasta", libre, ui.problemas[ReporteCampo.HASTA])
            OpcionesFila(R.string.reportes_field_fecha_base, FechaBase.entries, ui.form.fechaBase, libre,
                { base -> stringResource(when (base) {
                    FechaBase.FECHA -> R.string.reportes_base_fecha
                    FechaBase.FECHA_ENVIO -> R.string.reportes_base_envio
                    FechaBase.FECHA_ENTREGA -> R.string.reportes_base_entrega
                }) }, "reporteBase_") { v -> vm.cambiar { it.copy(fechaBase = v) } }
            OpcionesFila(R.string.reportes_page_size, TAMANOS_PAGINA, ui.form.tamano, libre,
                { n -> stringResource(R.string.reportes_page_size_option, n) }, "reporteTamano_") { v -> vm.cambiar { it.copy(tamano = v) } }
        },
        filas = { pagina -> items(pagina.contenido, key = { it.id }) { PedidoReporteCard(it) } },
        pie = { pagina -> Paginador(pagina, ui.busy, vm::paginaAnterior, vm::paginaSiguiente) }
    )
}
