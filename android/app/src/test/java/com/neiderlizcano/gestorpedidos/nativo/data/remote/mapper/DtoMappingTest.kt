package com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.neiderlizcano.gestorpedidos.nativo.core.network.InvalidPayloadException
import com.neiderlizcano.gestorpedidos.nativo.core.network.NetworkJson
import com.neiderlizcano.gestorpedidos.nativo.data.*
import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class DtoMappingTest {
    private val gson = NetworkJson.create()
    private fun invalid(block: () -> Unit) {
        try { block(); fail("Invalid payload accepted") } catch (_: InvalidPayloadException) { }
        catch (_: com.google.gson.JsonParseException) { }
    }
    @Test fun allThirteenOrderFieldsAreRequiredForCrudAndReports() {
        val original = gson.fromJson(ORDER_JSON, JsonObject::class.java)
        assertEquals(13, original.size())
        for (field in original.keySet()) {
            for (explicitNull in listOf(false, true)) {
                val wire = original.deepCopy()
                if (explicitNull) wire.add(field, JsonNull.INSTANCE) else wire.remove(field)
                invalid { gson.fromJson(wire, PedidoDto::class.java).toPedido() }
                invalid { gson.fromJson(wire, PedidoDto::class.java).toReporte() }
            }
        }
    }
    @Test fun allFourProfileFieldsAreRequiredAndUuidIsCanonical() {
        val original = gson.fromJson(PROFILE_JSON, JsonObject::class.java)
        for (field in original.keySet()) {
            for (explicitNull in listOf(false, true)) {
                val wire = original.deepCopy()
                if (explicitNull) wire.add(field, JsonNull.INSTANCE) else wire.remove(field)
                invalid { gson.fromJson(wire, PerfilDto::class.java).toPerfil() }
                invalid { gson.fromJson(wire, PerfilDto::class.java).toReporte() }
            }
        }
        for (id in listOf("invalid", "1-1-1-1-1")) {
            original.addProperty("id", id)
            invalid { gson.fromJson(original, PerfilDto::class.java).toPerfil() }
        }
    }
    @Test fun pageFieldsCannotSilentlyDefaultToZeroFalseOrEmpty() {
        val original = gson.fromJson(pageJson(), JsonObject::class.java)
        for (field in original.keySet()) {
            for (explicitNull in listOf(false, true)) {
                val wire = original.deepCopy()
                if (explicitNull) wire.add(field, JsonNull.INSTANCE) else wire.remove(field)
                invalid { gson.fromJson(wire, PaginaPedidosDto::class.java).toPagina() }
            }
        }
    }
    @Test fun wrongJsonPrimitivesAndOverflowAreRejectedInsteadOfCoerced() {
        for (entry in listOf("id" to JsonPrimitive(1.5), "id" to JsonPrimitive("7"),
            "id" to JsonPrimitive(BigDecimal("9223372036854775808")), "cliente" to JsonPrimitive(42))) {
            val wire = gson.fromJson(ORDER_JSON, JsonObject::class.java)
            wire.add(entry.first, entry.second)
            invalid { gson.fromJson(wire, PedidoDto::class.java).toPedido() }
        }
        for (entry in listOf("pagina" to JsonPrimitive(0.5), "tamano" to JsonPrimitive("20"), "haySiguiente" to JsonPrimitive("false"))) {
            val wire = gson.fromJson(pageJson(), JsonObject::class.java)
            wire.add(entry.first, entry.second)
            invalid { gson.fromJson(wire, PaginaPedidosDto::class.java).toPagina() }
        }
    }
    @Test fun pageRejectsNullRowsAndImpossibleResponseMetadata() {
        for (wire in listOf(pageJson("null"), pageJson(page = -1), pageJson().replace("\"tamano\":20", "\"tamano\":0"))) {
            invalid { gson.fromJson(wire, PaginaPedidosDto::class.java).toPagina() }
        }
    }
    @Test fun mappingPreservesEveryFieldAndNeverAddsOwner() {
        val dto = gson.fromJson(ORDER_JSON, PedidoDto::class.java)
        val pedido = dto.toPedido()
        val reporte = dto.toReporte()
        // Compara todos los campos para detectar pérdidas o cambios durante el mapeo.
        assertEquals(gson.toJsonTree(pedido), gson.toJsonTree(reporte))
        assertEquals(gson.fromJson(ORDER_JSON, JsonObject::class.java), gson.toJsonTree(pedido))
        assertEquals(LocalDate.of(2026, 11, 15), reporte.fechaEnvio)
        val perfil = gson.fromJson(PROFILE_JSON, PerfilDto::class.java)
        assertEquals(gson.toJsonTree(perfil.toPerfil()), gson.toJsonTree(perfil.toReporte()))
    }
    @Test fun decimalPrecisionAndScaleSurviveWireDomainAndRequestMapping() {
        val exact = "1234567890.123456789012345678900"
        val pedido = gson.fromJson(ORDER_JSON.replace("1234.50", exact), PedidoDto::class.java).toPedido()
        assertEquals(BigDecimal(exact), pedido.valor)
        val request = PedidoSolicitudDto(pedido.fecha, pedido.fechaEnvio, pedido.fechaEntrega, pedido.cliente,
            pedido.proveedor, pedido.valor, pedido.estado, pedido.pais, pedido.departamento, pedido.ciudad,
            pedido.nomenclaturaVivienda, pedido.propina)
        val json = gson.toJsonTree(request).asJsonObject
        assertEquals(BigDecimal(exact), json["valor"].asBigDecimal)
        assertTrue(json["valor"].asJsonPrimitive.isNumber)
        assertFalse(json.has("id"))
        assertFalse(json.has("usuarioId"))
        assertFalse(json.has("usuario_id"))
    }
}
