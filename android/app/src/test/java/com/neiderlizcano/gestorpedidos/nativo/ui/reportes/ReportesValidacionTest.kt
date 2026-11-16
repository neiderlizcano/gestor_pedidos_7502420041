package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CampoPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CoincidenciaPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FechaBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportesValidacionTest {
    private fun <F> ValidacionFiltro<F>.problemas() = (this as ValidacionFiltro.Invalido).problemas
    private fun <F> ValidacionFiltro<F>.filtro() = (this as ValidacionFiltro.Valido).filtro

    @Test fun estadoFechasSinFiltrosEsValidoYUsaNulos() {
        val filtro = validarEstadoFechas(FormularioEstadoFechas()).filtro()
        assertNull(filtro.estado); assertNull(filtro.desde); assertNull(filtro.hasta)
        assertEquals(FechaBase.FECHA, filtro.fechaBase)
    }

    @Test fun estadoFechasRecortaTextoYConvierteFechas() {
        val form = FormularioEstadoFechas("  ENVIADO ", "2026-11-01", " 2026-11-30 ", FechaBase.FECHA_ENTREGA)
        val filtro = validarEstadoFechas(form).filtro()
        assertEquals("ENVIADO", filtro.estado)
        assertEquals(LocalDate.parse("2026-11-01"), filtro.desde)
        assertEquals(LocalDate.parse("2026-11-30"), filtro.hasta)
        assertEquals(FechaBase.FECHA_ENTREGA, filtro.fechaBase)
    }

    @Test fun fechasInexistentesOConFormatoIncorrectoSeRechazan() {
        for (texto in listOf("2026-02-30", "15/11/2026", "2026-1-1", "hoy", "2026-13-01")) {
            val problemas = validarEstadoFechas(FormularioEstadoFechas(desde = texto)).problemas()
            assertEquals(texto, ReporteProblema.FECHA_ISO, problemas[ReporteCampo.DESDE])
        }
        assertEquals(ReporteProblema.FECHA_ISO, validarEstadoFechas(FormularioEstadoFechas(hasta = "x")).problemas()[ReporteCampo.HASTA])
    }

    @Test fun rangoInvertidoSeRechazaPeroLimitesIgualesSeAceptan() {
        val invertido = FormularioEstadoFechas(desde = "2026-11-10", hasta = "2026-11-01")
        assertEquals(ReporteProblema.RANGO_FECHAS, validarEstadoFechas(invertido).problemas()[ReporteCampo.DESDE])
        assertTrue(validarEstadoFechas(FormularioEstadoFechas(desde = "2026-11-10", hasta = "2026-11-10")) is ValidacionFiltro.Valido)
    }

    @Test fun ubicacionExigeAlMenosUnFiltro() {
        assertEquals(ReporteProblema.FILTRO_REQUERIDO,
            validarUbicacion(FormularioUbicacion(pais = "   ")).problemas()[ReporteCampo.UBICACION])
        val filtro = validarUbicacion(FormularioUbicacion(ciudad = " Cartagena ")).filtro()
        assertNull(filtro.pais); assertNull(filtro.departamento); assertEquals("Cartagena", filtro.ciudad)
    }

    @Test fun terminoObligatorioConLimiteDe200() {
        assertEquals(ReporteProblema.OBLIGATORIO, validarNombreUsername(FormularioNombreUsername(termino = "  ")).problemas()[ReporteCampo.TERMINO])
        assertEquals(ReporteProblema.DEMASIADO_LARGO,
            validarNombreUsername(FormularioNombreUsername(termino = "a".repeat(201))).problemas()[ReporteCampo.TERMINO])
        val filtro = validarNombreUsername(FormularioNombreUsername(CampoPerfil.USERNAME, " ana ", CoincidenciaPerfil.EXACTA)).filtro()
        assertEquals("ana", filtro.termino); assertEquals(CampoPerfil.USERNAME, filtro.campo); assertEquals(CoincidenciaPerfil.EXACTA, filtro.coincidencia)
        assertTrue(validarNombreUsername(FormularioNombreUsername(termino = "a".repeat(200))) is ValidacionFiltro.Valido)
    }

    @Test fun correoValidaFormatoYLongitud() {
        assertEquals(ReporteProblema.OBLIGATORIO, validarCorreo(FormularioCorreo("")).problemas()[ReporteCampo.CORREO])
        assertEquals(ReporteProblema.CORREO_INVALIDO, validarCorreo(FormularioCorreo("sin-arroba")).problemas()[ReporteCampo.CORREO])
        assertEquals(ReporteProblema.DEMASIADO_LARGO,
            validarCorreo(FormularioCorreo("a".repeat(250) + "@e.co")).problemas()[ReporteCampo.CORREO])
        assertEquals("persona@example.invalid", validarCorreo(FormularioCorreo(" persona@example.invalid ")).filtro())
    }
}
