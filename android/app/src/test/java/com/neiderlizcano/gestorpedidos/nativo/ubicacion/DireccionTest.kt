package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Direcciones ficticias; las pruebas no usan datos personales ni coordenadas reales de personas.
class DireccionTest {
    private val completa = DireccionCruda("Colombia", "Bolívar", "Cartagena", "Cartagena de Indias", "Carrera 5", "12-34")

    @Test fun direccionCompletaLlenaLosCuatroCamposSinAvisos() {
        val s = completa.aSugerencia()
        assertEquals("Colombia", s.pais); assertEquals("Bolívar", s.departamento)
        assertEquals("Cartagena de Indias", s.ciudad); assertEquals("Carrera 5 #12-34", s.nomenclatura)
        assertTrue(s.faltantes.isEmpty()); assertTrue(s.avisos.isEmpty()); assertFalse(s.vacia)
    }

    @Test fun elNumeroNoDuplicaElSimboloNiLaCalleQueYaLoContiene() {
        assertEquals("Calle 10 #5-20", completa.copy(calle = "Calle 10", numero = "#5-20").aSugerencia().nomenclatura)
        assertEquals("Calle 10 #5-20", completa.copy(calle = "Calle 10 #5-20", numero = "5-20").aSugerencia().nomenclatura)
    }

    @Test fun conCalleSinNumeroSeSugiereSoloLaCalleYSeAvisa() {
        val s = completa.copy(numero = null).aSugerencia()
        assertEquals("Carrera 5", s.nomenclatura)
        assertTrue(AvisoDireccion.SIN_NUMERO in s.avisos)
        assertFalse(CampoDireccion.NOMENCLATURA in s.faltantes)
    }

    @Test fun unNumeroSinCalleNoSeUsaPorqueNoIdentificaUnaVivienda() {
        val s = completa.copy(calle = null, numero = "12-34").aSugerencia()
        assertNull(s.nomenclatura)
        assertTrue(AvisoDireccion.NUMERO_SIN_CALLE in s.avisos)
        assertTrue(CampoDireccion.NOMENCLATURA in s.faltantes)
    }

    @Test fun sinLocalidadSeUsaElMunicipioPeroSeMarcaComoAproximado() {
        val s = completa.copy(localidad = null).aSugerencia()
        assertEquals("Cartagena", s.ciudad)
        assertTrue(AvisoDireccion.CIUDAD_APROXIMADA in s.avisos)
    }

    @Test fun nuncaSeInventaLoQueFalta() {
        val s = DireccionCruda(pais = "Colombia").aSugerencia()
        assertEquals("Colombia", s.pais)
        assertNull(s.departamento); assertNull(s.ciudad); assertNull(s.nomenclatura)
        assertEquals(setOf(CampoDireccion.DEPARTAMENTO, CampoDireccion.CIUDAD, CampoDireccion.NOMENCLATURA), s.faltantes)
    }

    @Test fun textosVaciosOSoloEspaciosCuentanComoAusentes() {
        val s = DireccionCruda("  ", "", " \t", null, "   ", "").aSugerencia()
        assertTrue(s.vacia)
        assertEquals(CampoDireccion.entries.toSet(), s.faltantes)
    }

    @Test fun seNormalizanEspaciosInternosYExternos() {
        val s = completa.copy(calle = "  Calle    10  ").aSugerencia()
        assertEquals("Calle 10 #12-34", s.nomenclatura)
    }

    @Test fun laUbicacionAproximadaAgregaUnAvisoSinCambiarLosDatos() {
        val s = completa.aSugerencia(precisionAproximada = true)
        assertTrue(AvisoDireccion.PRECISION_APROXIMADA in s.avisos)
        assertEquals("Carrera 5 #12-34", s.nomenclatura)
    }

    @Test fun elNombreDeLugarSoloSeAceptaComoNumeroSiTieneFormaDeNumero() {
        assertEquals("5-20", numeroDesdeNombreDeLugar("5-20", "Calle 10", "Cartagena"))
        assertEquals("12 - 34", numeroDesdeNombreDeLugar(" 12 - 34 ", "Carrera 5", null))
        assertEquals("45A", numeroDesdeNombreDeLugar("45A", "Calle 10", "Cartagena"))
        assertNull(numeroDesdeNombreDeLugar("Parque Centenario", "Calle 10", "Cartagena"))
        assertNull(numeroDesdeNombreDeLugar("Calle 10", "Calle 10", "Cartagena"))
        assertNull(numeroDesdeNombreDeLugar("Cartagena", "Calle 10", "Cartagena"))
        assertNull(numeroDesdeNombreDeLugar(null, "Calle 10", "Cartagena"))
        assertNull(numeroDesdeNombreDeLugar("   ", "Calle 10", "Cartagena"))
        assertNull(numeroDesdeNombreDeLugar("1234567890123456789012", "Calle 10", "Cartagena"))
    }
}
