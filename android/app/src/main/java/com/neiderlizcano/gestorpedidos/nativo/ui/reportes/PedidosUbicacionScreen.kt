package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PaginaReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository

/** Pantalla del reporte de pedidos por país, departamento y ciudad. */
@Composable
fun PedidosUbicacionRoute(repositorio: ReportesRepository) {
    val factory = remember(repositorio) {
        reporteFactory(PedidosUbicacionViewModel::class.java) { PedidosUbicacionViewModel(repositorio) }
    }
    val vm: PedidosUbicacionViewModel = viewModel(factory = factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    PedidosUbicacionScreen(ui, vm)
}

@Composable
internal fun PedidosUbicacionScreen(ui: ReporteUi<FormularioUbicacion, PaginaReporte<PedidoReporte>>, vm: PedidosUbicacionViewModel) {
    ReporteLayout(
        titulo = R.string.reportes_menu_pedidos_ubicacion, ui = ui,
        onConsultar = vm::consultar, onLimpiar = vm::limpiar, onReintentar = vm::reintentar,
        vacio = { it.contenido.isEmpty() },
        filtros = {
            val libre = !ui.busy
            CampoFiltro(ui.form.pais, { v -> vm.cambiar { it.copy(pais = v) } }, R.string.reportes_field_pais,
                "reporteCampo_pais", libre, null)
            CampoFiltro(ui.form.departamento, { v -> vm.cambiar { it.copy(departamento = v) } }, R.string.reportes_field_departamento,
                "reporteCampo_departamento", libre, null)
            CampoFiltro(ui.form.ciudad, { v -> vm.cambiar { it.copy(ciudad = v) } }, R.string.reportes_field_ciudad,
                "reporteCampo_ciudad", libre, null)
            // La regla "al menos un filtro" afecta al conjunto, no a un campo concreto.
            ui.problemas[ReporteCampo.UBICACION]?.let {
                Text(stringResource(mensajeProblema(it)), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("reporteProblema_ubicacion"))
            }
            OpcionesFila(R.string.reportes_page_size, TAMANOS_PAGINA, ui.form.tamano, libre,
                { n -> stringResource(R.string.reportes_page_size_option, n) }, "reporteTamano_") { v -> vm.cambiar { it.copy(tamano = v) } }
        },
        filas = { pagina -> items(pagina.contenido, key = { it.id }) { PedidoReporteCard(it) } },
        pie = { pagina -> Paginador(pagina, ui.busy, vm::paginaAnterior, vm::paginaSiguiente) }
    )
}
