package com.neiderlizcano.gestorpedidos.nativo.sensores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorMovimientoTest {
    private fun lectura(x: Float, y: Float, z: Float, ns: Long = 0L) = LecturaAcelerometro(x, y, z, ns, false)

    @Test fun elReposoNoSeConsideraMovimientoEnCualquierOrientacion() {
        val detector = DetectorMovimiento()
        listOf(lectura(0f, 9.81f, 0f), lectura(9.81f, 0f, 0f), lectura(0f, 0f, -9.81f), lectura(5.66f, 5.66f, 5.66f)).forEach {
            val r = detector.procesar(it)
            assertFalse(r.movimiento); assertFalse(r.deteccionNueva)
        }
    }

    @Test fun unaSacudidaFuerteSeDetecta() {
        val r = DetectorMovimiento().procesar(lectura(0f, 22f, 0f))
        assertTrue(r.movimiento); assertTrue(r.deteccionNueva)
        assertEquals(12.19f, r.desviacion, 0.01f)
    }

    @Test fun unaCaidaLibreTambienEsMovimientoSignificativo() {
        // En caída libre la magnitud tiende a 0: la desviación respecto de la gravedad es ≈ 9,81.
        assertTrue(DetectorMovimiento().procesar(lectura(0f, 0.2f, 0f)).movimiento)
    }

    @Test fun elUmbralEsInclusivoYLasMuestrasPorDebajoNoCuentan() {
        val detector = DetectorMovimiento(umbralMs2 = 3f)
        assertFalse(detector.procesar(lectura(0f, 9.80665f + 2.9f, 0f)).movimiento)
        assertTrue(detector.procesar(lectura(0f, 9.80665f + 3.01f, 0f)).movimiento)
    }

    @Test fun lasMuestrasDeUnaMismaSacudidaCuentanUnaSolaVez() {
        val detector = DetectorMovimiento(enfriamientoNs = ms(1000))
        assertTrue(detector.procesar(lectura(0f, 20f, 0f, ms(0))).deteccionNueva)
        val siguiente = detector.procesar(lectura(0f, 21f, 0f, ms(200)))
        assertTrue(siguiente.movimiento); assertFalse(siguiente.deteccionNueva)
        assertFalse(detector.procesar(lectura(0f, 20f, 0f, ms(999))).deteccionNueva)
        assertTrue(detector.procesar(lectura(0f, 20f, 0f, ms(1000))).deteccionNueva)
    }

    @Test fun elReposoNoReiniciaElEnfriamiento() {
        val detector = DetectorMovimiento(enfriamientoNs = ms(1000))
        assertTrue(detector.procesar(lectura(0f, 20f, 0f, ms(0))).deteccionNueva)
        detector.procesar(lectura(0f, 9.81f, 0f, ms(500)))
        assertFalse(detector.procesar(lectura(0f, 20f, 0f, ms(600))).deteccionNueva)
    }

    @Test fun laMagnitudEsLaNormaDelVector() {
        assertEquals(13f, lectura(3f, 4f, 12f).magnitud(), 0.0001f)
    }
}
