package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neiderlizcano.gestorpedidos.nativo.R
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CoincidenciaPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PerfilReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository

/** Pantalla del reporte de perfiles por nombre. */
@Composable
fun PerfilesNombreUsernameRoute(repositorio: ReportesRepository) {
    val factory = remember(repositorio) {
        reporteFactory(PerfilesNombreUsernameViewModel::class.java) { PerfilesNombreUsernameViewModel(repositorio) }
    }
    val vm: PerfilesNombreUsernameViewModel = viewModel(factory = factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    PerfilesNombreUsernameScreen(ui, vm)
}

@Composable
internal fun PerfilesNombreUsernameScreen(ui: ReporteUi<FormularioNombreUsername, List<PerfilReporte>>, vm: PerfilesNombreUsernameViewModel) {
    ReporteLayout(
        titulo = R.string.reportes_menu_usuarios_nombre, ui = ui,
        onConsultar = vm::consultar, onLimpiar = vm::limpiar, onReintentar = vm::reintentar,
        vacio = { it.isEmpty() },
        filtros = {
            val libre = !ui.busy
            // La búsqueda es siempre por nombre (el formulario conserva CampoPerfil.NOMBRE); no se ofrece elegir username.
            OpcionesFila(R.string.reportes_field_coincidencia, CoincidenciaPerfil.entries, ui.form.coincidencia, libre,
                { c -> stringResource(if (c == CoincidenciaPerfil.EXACTA) R.string.reportes_coincidencia_exacta else R.string.reportes_coincidencia_prefijo) },
                "reporteCoincidencia_") { v -> vm.cambiar { it.copy(coincidencia = v) } }
            CampoFiltro(ui.form.termino, { v -> vm.cambiar { it.copy(termino = v) } }, R.string.reportes_field_termino,
                "reporteCampo_termino", libre, ui.problemas[ReporteCampo.TERMINO])
        },
        filas = { perfiles -> items(perfiles, key = { it.id }) { PerfilReporteCard(it) } }
    )
}

/** Pantalla del reporte de perfiles por correo. */
@Composable
fun PerfilesCorreoRoute(repositorio: ReportesRepository) {
    val factory = remember(repositorio) {
        reporteFactory(PerfilesCorreoViewModel::class.java) { PerfilesCorreoViewModel(repositorio) }
    }
    val vm: PerfilesCorreoViewModel = viewModel(factory = factory)
    val ui by vm.uiState.collectAsStateWithLifecycle()
    PerfilesCorreoScreen(ui, vm)
}

@Composable
internal fun PerfilesCorreoScreen(ui: ReporteUi<FormularioCorreo, List<PerfilReporte>>, vm: PerfilesCorreoViewModel) {
    ReporteLayout(
        titulo = R.string.reportes_menu_usuarios_correo, ui = ui,
        onConsultar = vm::consultar, onLimpiar = vm::limpiar, onReintentar = vm::reintentar,
        vacio = { it.isEmpty() },
        filtros = {
            CampoFiltro(ui.form.correo, { v -> vm.cambiar { it.copy(correo = v) } }, R.string.reportes_field_correo,
                "reporteCampo_correo", !ui.busy, ui.problemas[ReporteCampo.CORREO], KeyboardType.Email)
        },
        filas = { perfiles -> items(perfiles, key = { it.id }) { PerfilReporteCard(it) } }
    )
}
