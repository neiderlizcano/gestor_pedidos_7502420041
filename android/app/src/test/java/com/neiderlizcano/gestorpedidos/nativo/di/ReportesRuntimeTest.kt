package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportesRuntimeTest {
    private val esperado = AppResult.Fallo(AppError.ServicioNoDisponible)
    @Test fun sinConfiguracionEstadoFechasNoInventaDatos() = runTest {
        assertEquals(esperado, UnavailableReportesRepository.pedidosPorEstadoFechas(FiltroEstadoFechas()))
    }
    @Test fun sinConfiguracionUbicacionNoInventaDatos() = runTest {
        assertEquals(esperado, UnavailableReportesRepository.pedidosPorUbicacion(FiltroUbicacion(pais = "Colombia")))
    }
    @Test fun sinConfiguracionNombreNoInventaPerfil() = runTest {
        assertEquals(esperado, UnavailableReportesRepository.perfilesPorNombreUsername(FiltroNombreUsername(CampoPerfil.NOMBRE, "Persona", CoincidenciaPerfil.PREFIJO)))
    }
    @Test fun sinConfiguracionCorreoNoInventaPerfil() = runTest {
        assertEquals(esperado, UnavailableReportesRepository.perfilesPorCorreo("persona@example.invalid"))
    }
}
