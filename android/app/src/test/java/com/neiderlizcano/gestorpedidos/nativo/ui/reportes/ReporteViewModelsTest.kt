package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CampoPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CoincidenciaPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FechaBase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReporteViewModelsTest {
    private fun pedidos(n: Int) = (1..n).map { pedidoReporte(it.toLong()) }

    @Test fun filtroInvalidoNuncaLlamaAlRepositorio() = runTest {
        val repo = FakeReportes(pedidos(3)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.cambiar { it.copy(desde = "2026-02-30") }
        vm.consultar(); runCurrent()
        assertEquals(ReporteProblema.FECHA_ISO, vm.uiState.value.problemas[ReporteCampo.DESDE])
        assertEquals(0, repo.peticiones)
        assertFalse(vm.uiState.value.busy)
    }

    @Test fun consultaEnviaFiltrosNormalizadosYMuestraPrimeraPagina() = runTest {
        val repo = FakeReportes(pedidos(25)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.cambiar { it.copy(estado = " ENVIADO ", desde = "2026-11-01", fechaBase = FechaBase.FECHA_ENVIO, tamano = 10) }
        vm.consultar(); runCurrent()
        val (filtro, pagina) = repo.ultimoEstadoFechas!!
        assertEquals("ENVIADO", filtro.estado); assertEquals(LocalDate.parse("2026-11-01"), filtro.desde)
        assertEquals(FechaBase.FECHA_ENVIO, filtro.fechaBase)
        assertEquals(0, pagina.pagina); assertEquals(10, pagina.tamano)
        val datos = vm.uiState.value.datos!!
        assertEquals(10, datos.contenido.size); assertTrue(datos.haySiguiente); assertTrue(vm.uiState.value.buscado)
    }

    @Test fun paginacionUsaElFiltroAplicadoNoLoEditadoDespues() = runTest {
        val repo = FakeReportes(pedidos(25)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.cambiar { it.copy(estado = "ENVIADO", tamano = 10) }
        vm.consultar(); runCurrent()
        vm.cambiar { it.copy(estado = "OTRO") }
        vm.paginaSiguiente(); runCurrent()
        assertEquals("ENVIADO", repo.ultimoEstadoFechas!!.first.estado)
        assertEquals(1, repo.ultimoEstadoFechas!!.second.pagina)
        vm.paginaSiguiente(); runCurrent()
        assertEquals(5, vm.uiState.value.datos!!.contenido.size); assertFalse(vm.uiState.value.datos!!.haySiguiente)
        vm.paginaSiguiente(); runCurrent()
        assertEquals(3, repo.peticiones)
        vm.paginaAnterior(); runCurrent()
        assertEquals(1, vm.uiState.value.datos!!.pagina)
    }

    @Test fun paginaAnteriorEnLaPrimeraNoHaceNada() = runTest {
        val repo = FakeReportes(pedidos(25)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.consultar(); runCurrent(); vm.paginaAnterior(); runCurrent()
        assertEquals(1, repo.peticiones)
    }

    @Test fun resultadoVacioSeDistingueDeNoBuscarYDeError() = runTest {
        val repo = FakeReportes(emptyList()); val vm = PedidosUbicacionViewModel(repo, backgroundScope)
        assertNull(vm.uiState.value.datos); assertFalse(vm.uiState.value.buscado)
        vm.cambiar { it.copy(pais = "Colombia") }; vm.consultar(); runCurrent()
        assertTrue(vm.uiState.value.datos!!.contenido.isEmpty())
        assertTrue(vm.uiState.value.buscado); assertNull(vm.uiState.value.error)
    }

    @Test fun ubicacionSinFiltrosNoConsultaYConFiltroEnviaSoloLoDeclarado() = runTest {
        val repo = FakeReportes(pedidos(2)); val vm = PedidosUbicacionViewModel(repo, backgroundScope)
        vm.consultar(); runCurrent()
        assertEquals(ReporteProblema.FILTRO_REQUERIDO, vm.uiState.value.problemas[ReporteCampo.UBICACION]); assertEquals(0, repo.peticiones)
        vm.cambiar { it.copy(ciudad = "Cartagena") }; vm.consultar(); runCurrent()
        val filtro = repo.ultimaUbicacion!!.first
        assertNull(filtro.pais); assertNull(filtro.departamento); assertEquals("Cartagena", filtro.ciudad)
    }

    @Test fun errorEnPaginaConservaLaPaginaActualYReintentaLaMisma() = runTest {
        val repo = FakeReportes(pedidos(25)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.cambiar { it.copy(tamano = 10) }; vm.consultar(); runCurrent()
        repo.error = AppError.ErrorConexion
        vm.paginaSiguiente(); runCurrent()
        assertEquals(AppError.ErrorConexion, vm.uiState.value.error)
        assertEquals(0, vm.uiState.value.datos!!.pagina)
        repo.error = null; vm.reintentar(); runCurrent()
        assertEquals(1, vm.uiState.value.datos!!.pagina); assertNull(vm.uiState.value.error)
    }

    @Test fun errorEnConsultaNuevaNoMuestraFilasDeUnFiltroAnterior() = runTest {
        val repo = FakeReportes(pedidos(3)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.consultar(); runCurrent()
        assertEquals(3, vm.uiState.value.datos!!.contenido.size)
        repo.error = AppError.ServicioNoDisponible
        vm.cambiar { it.copy(estado = "CANCELADO") }; vm.consultar(); runCurrent()
        assertNull(vm.uiState.value.datos); assertFalse(vm.uiState.value.buscado)
        assertEquals(AppError.ServicioNoDisponible, vm.uiState.value.error)
    }

    @Test fun mientrasCargaSeIgnoranConsultasDuplicadasYCambios() = runTest {
        val repo = FakeReportes(pedidos(3)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        repo.release = CompletableDeferred()
        vm.consultar(); runCurrent()
        assertTrue(vm.uiState.value.busy)
        vm.consultar(); vm.cambiar { it.copy(estado = "X") }; vm.limpiar(); runCurrent()
        assertEquals(1, repo.peticiones); assertEquals("", vm.uiState.value.form.estado)
        repo.release!!.complete(Unit); runCurrent()
        assertFalse(vm.uiState.value.busy); assertEquals(3, vm.uiState.value.datos!!.contenido.size)
    }

    @Test fun limpiarRestableceFormularioYResultados() = runTest {
        val repo = FakeReportes(pedidos(3)); val vm = PedidosEstadoFechasViewModel(repo, backgroundScope)
        vm.cambiar { it.copy(estado = "ENVIADO") }; vm.consultar(); runCurrent()
        vm.limpiar()
        assertEquals(FormularioEstadoFechas(), vm.uiState.value.form); assertNull(vm.uiState.value.datos)
        vm.paginaSiguiente(); runCurrent()
        assertEquals(1, repo.peticiones)
    }

    @Test fun reportesDePerfilesEnviaCampoCoincidenciaYCorreoNormalizados() = runTest {
        val repo = FakeReportes(perfiles = listOf(perfilReporte("ana")))
        val porNombre = PerfilesNombreUsernameViewModel(repo, backgroundScope)
        porNombre.cambiar { it.copy(campo = CampoPerfil.USERNAME, termino = " ana ", coincidencia = CoincidenciaPerfil.EXACTA) }
        porNombre.consultar(); runCurrent()
        assertEquals("ana", repo.ultimoNombre!!.termino); assertEquals(CampoPerfil.USERNAME, repo.ultimoNombre!!.campo)
        assertEquals(1, porNombre.uiState.value.datos!!.size)
        val porCorreo = PerfilesCorreoViewModel(repo, backgroundScope)
        porCorreo.cambiar { it.copy(correo = " ana@example.invalid ") }; porCorreo.consultar(); runCurrent()
        assertEquals("ana@example.invalid", repo.ultimoCorreo)
        porCorreo.cambiar { it.copy(correo = "no-es-correo") }; porCorreo.consultar(); runCurrent()
        assertEquals(ReporteProblema.CORREO_INVALIDO, porCorreo.uiState.value.problemas[ReporteCampo.CORREO])
        assertEquals(2, repo.peticiones)
    }

    @Test fun perfilesVaciosYErrorDeSesionSeMuestranSinCerrarSesion() = runTest {
        val repo = FakeReportes(perfiles = emptyList()); val vm = PerfilesCorreoViewModel(repo, backgroundScope)
        vm.cambiar { it.copy(correo = "nadie@example.invalid") }; vm.consultar(); runCurrent()
        assertTrue(vm.uiState.value.datos!!.isEmpty())
        repo.error = AppError.SesionNoValida; vm.reintentar(); runCurrent()
        assertEquals(AppError.SesionNoValida, vm.uiState.value.error)
    }

    @Test fun cadaViewModelUsaSoloElRepositorioDeSuSesion() = runTest {
        val repoA = FakeReportes(listOf(pedidoReporte(1))); val repoB = FakeReportes(listOf(pedidoReporte(2), pedidoReporte(3)))
        val a = PedidosUbicacionViewModel(repoA, backgroundScope); val b = PedidosUbicacionViewModel(repoB, backgroundScope)
        a.cambiar { it.copy(pais = "Colombia") }; b.cambiar { it.copy(pais = "Colombia") }
        a.consultar(); b.consultar(); runCurrent()
        assertEquals(listOf(1L), a.uiState.value.datos!!.contenido.map { it.id })
        assertEquals(listOf(2L, 3L), b.uiState.value.datos!!.contenido.map { it.id })
    }

    @Test fun alVaciarElAlmacenDeLaSesionUnaRespuestaTardiaSeDescarta() = runTest {
        val principal = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(principal)
        try {
            val repo = FakeReportes(pedidos(3)); val almacen = ViewModelStore()
            val factory = reporteFactory(PedidosEstadoFechasViewModel::class.java) { PedidosEstadoFechasViewModel(repo) }
            val vm = ViewModelProvider(almacen, factory)[PedidosEstadoFechasViewModel::class.java]
            repo.release = CompletableDeferred()
            vm.consultar(); runCurrent()
            assertTrue(vm.uiState.value.busy)
            almacen.clear() // Logout o cambio de cuenta: el almacén privado se descarta.
            repo.release!!.complete(Unit); runCurrent()
            assertNull(vm.uiState.value.datos); assertFalse(vm.uiState.value.buscado)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
