package com.neiderlizcano.gestorpedidos.nativo.core.network

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NetworkJsonTest {
    private val gson = NetworkJson.create()
    @Test fun decimalRoundTripPreservesPrecision() {
        val value = BigDecimal("99999999999999.12345678901234567890")
        assertEquals(value, gson.fromJson(gson.toJson(value), BigDecimal::class.java))
    }
    @Test fun isoDateRoundTrip() {
        val value = LocalDate.of(2024, 2, 29)
        assertEquals("\"2024-02-29\"", gson.toJson(value))
        assertEquals(value, gson.fromJson("\"2024-02-29\"", LocalDate::class.java))
    }
    @Test fun explicitNullRemainsNullForMapperValidation() {
        assertNull(gson.fromJson("null", BigDecimal::class.java))
        try { requiredField<String>(null); fail("Missing field accepted") } catch (_: InvalidPayloadException) { }
    }
    @Test fun rejectsNonIsoDateAndNonNumericMoney() {
        for (value in listOf("\"29/02/2024\"", "\"2024-2-29\"", "\"2024-02-30\"")) {
            try { gson.fromJson(value, LocalDate::class.java); fail("Bad date accepted") } catch (_: Exception) { }
        }
        try { gson.fromJson("\"12.34\"", BigDecimal::class.java); fail("String amount accepted") } catch (_: Exception) { }
    }
}
