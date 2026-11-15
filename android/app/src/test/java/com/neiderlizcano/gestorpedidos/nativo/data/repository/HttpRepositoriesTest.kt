package com.neiderlizcano.gestorpedidos.nativo.data.repository

import com.neiderlizcano.gestorpedidos.nativo.core.network.*
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.core.session.AccessTokenProvider
import com.neiderlizcano.gestorpedidos.nativo.core.session.TokenUnavailableException
import com.neiderlizcano.gestorpedidos.nativo.data.*
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.*
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.*
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.*
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.*
import com.neiderlizcano.gestorpedidos.nativo.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HttpRepositoriesTest {
    private lateinit var server: MockWebServer
    private lateinit var pedidos: PedidosRepository
    private lateinit var usuarios: UsuariosRepository
    private lateinit var reportes: ReportesRepository
    private lateinit var reportesApi: ReportesApi
    private val gson = NetworkJson.create()
    private val datosPerfil = DatosPerfil("persona", "Persona Ejemplo")
    private val datosPedido = DatosPedido(
        LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-16"),
        "Cliente Ejemplo", "Proveedor Ejemplo", BigDecimal("1234.50"), "En preparación", "Colombia",
        "Bolívar", "Cartagena", "Dirección ficticia", BigDecimal("0.00")
    )

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val base = ApiBaseUrl(server.url("/").newBuilder().host("localhost").build().toString())
        val transport = ApiNetwork.client(base).newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        val retrofit = ApiNetwork.retrofit(base, transport)
        val executor = ApiRequestExecutor(AccessTokenProvider { "test-token-only" })
        pedidos = HttpPedidosRepository(retrofit.create(PedidosApi::class.java), executor)
        usuarios = HttpUsuariosRepository(retrofit.create(UsuariosApi::class.java), executor)
        reportesApi = retrofit.create(ReportesApi::class.java)
        reportes = HttpReportesRepository(reportesApi, executor)
    }
    @After fun teardown() { server.shutdown() }
    private fun enqueue(json: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
    }
    private fun take(method: String, path: String): RecordedRequest {
        val request = server.takeRequest(2, TimeUnit.SECONDS) ?: error("No request")
        assertEquals(method, request.method)
        assertEquals(path, request.requestUrl!!.encodedPath)
        assertEquals("Bearer test-token-only", request.getHeader("Authorization"))
        return request
    }
    private fun <T> value(result: AppResult<T>): T {
        assertTrue("Expected success", result is AppResult.Exito)
        return (result as AppResult.Exito).valor
    }

    @Test fun createProfile201SendsOnlyEditableFields() = runBlocking {
        enqueue(PROFILE_JSON, 201)
        assertEquals("persona", value(usuarios.crearPerfil(datosPerfil)).username)
        val req = take("POST", "/api/v1/usuarios")
        assertNull(req.requestUrl!!.query)
        assertEquals(setOf("username", "nombre"), gson.fromJson(req.body.readUtf8(), com.google.gson.JsonObject::class.java).keySet())
    }
    @Test fun getOwnProfile200HasNoIdentityOrQuery() = runBlocking {
        enqueue(PROFILE_JSON)
        assertEquals("00000000-0000-0000-0000-000000000001", value(usuarios.consultarPerfilPropio()).id)
        assertNull(take("GET", "/api/v1/usuarios/me").requestUrl!!.query)
    }
    @Test fun updateOwnProfile200UsesPutAndOnlyTwoFields() = runBlocking {
        enqueue(PROFILE_JSON)
        value(usuarios.actualizarPerfilPropio(datosPerfil))
        val req = take("PUT", "/api/v1/usuarios/me")
        assertEquals(setOf("username", "nombre"), gson.fromJson(req.body.readUtf8(), com.google.gson.JsonObject::class.java).keySet())
    }
    @Test fun deleteOwnAccount204HasNoBodyQueryOrContentHeaders() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AppResult.Exito(Unit), usuarios.eliminarCuentaPropia())
        val req = take("DELETE", "/api/v1/usuarios/me")
        assertEquals(0L, req.bodySize)
        assertNull(req.requestUrl!!.query)
        assertNull(req.getHeader("Content-Type"))
        assertNull(req.getHeader("Transfer-Encoding"))
    }
    private fun assertOrderRequest(req: RecordedRequest) {
        val body = gson.fromJson(req.body.readUtf8(), com.google.gson.JsonObject::class.java)
        assertEquals(setOf("fecha", "fechaEnvio", "fechaEntrega", "cliente", "proveedor", "valor", "estado", "pais", "departamento", "ciudad", "nomenclaturaVivienda", "propina"), body.keySet())
        assertEquals("2026-11-14", body["fecha"].asString)
        assertEquals("2026-11-15", body["fechaEnvio"].asString)
        assertEquals("2026-11-16", body["fechaEntrega"].asString)
        assertEquals(BigDecimal("1234.50"), body["valor"].asBigDecimal)
        assertEquals(BigDecimal("0.00"), body["propina"].asBigDecimal)
        assertTrue(body["valor"].asJsonPrimitive.isNumber)
        assertTrue(body["propina"].asJsonPrimitive.isNumber)
    }
    @Test fun createOrder201KeepsThirteenResponseAttributesAndTwelveInputFields() = runBlocking {
        enqueue(ORDER_JSON, 201)
        val pedido = value(pedidos.crear(datosPedido))
        assertEquals(7L, pedido.id)
        assertEquals(datosPedido.valor, pedido.valor)
        assertEquals(datosPedido.fechaEntrega, pedido.fechaEntrega)
        assertOrderRequest(take("POST", "/api/v1/pedidos"))
    }
    @Test fun listOrders200SupportsArray() = runBlocking {
        enqueue("[$ORDER_JSON]")
        assertEquals(1, value(pedidos.listar()).size)
        take("GET", "/api/v1/pedidos")
        Unit
    }
    @Test fun emptyOrderArrayIsSuccess() = runBlocking {
        enqueue("[]")
        assertEquals(AppResult.Exito(emptyList<Pedido>()), pedidos.listar())
    }
    @Test fun getOrderUsesIdPath() = runBlocking {
        enqueue(ORDER_JSON)
        assertEquals("Cartagena", value(pedidos.consultar(7)).ciudad)
        take("GET", "/api/v1/pedidos/7")
        Unit
    }
    @Test fun updateOrder200DoesNotSendIdOrOwner() = runBlocking {
        enqueue(ORDER_JSON)
        value(pedidos.actualizar(7, datosPedido))
        assertOrderRequest(take("PUT", "/api/v1/pedidos/7"))
    }
    @Test fun deleteOrder204ReturnsUnit() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AppResult.Exito(Unit), pedidos.eliminar(7))
        assertEquals(0L, take("DELETE", "/api/v1/pedidos/7").bodySize)
    }
    @Test fun defaultReportFiltersAreOmittedWithoutSilentEmptyList() = runBlocking {
        enqueue(pageJson(content = ""))
        val page = value(reportes.pedidosPorEstadoFechas(FiltroEstadoFechas()))
        assertEquals(emptyList<PedidoReporte>(), page.contenido)
        assertFalse(page.haySiguiente)
        assertEquals(0, page.pagina)
        assertEquals(20, page.tamano)
        val url = take("GET", "/api/v1/reportes/pedidos/estado-fechas").requestUrl!!
        assertEquals(setOf("fechaBase", "pagina", "tamano"), url.queryParameterNames)
        assertEquals("fecha", url.queryParameter("fechaBase"))
        assertEquals("0", url.queryParameter("pagina"))
        assertEquals("20", url.queryParameter("tamano"))
    }
    @Test fun reportDatesPaginationAndEnumsUseExplicitWireValues() = runBlocking {
        for ((field, wire) in listOf(FechaBase.FECHA to "fecha", FechaBase.FECHA_ENVIO to "fecha_envio", FechaBase.FECHA_ENTREGA to "fecha_entrega")) {
            enqueue(pageJson(page = 2, next = true))
            val page = value(reportes.pedidosPorEstadoFechas(
                FiltroEstadoFechas("En preparación & +%", datosPedido.fecha, datosPedido.fechaEntrega, field), SolicitudPagina(2, 20)))
            assertTrue(page.haySiguiente)
            assertEquals(2, page.pagina)
            assertEquals(BigDecimal("1234.50"), page.contenido.single().valor)
            val url = take("GET", "/api/v1/reportes/pedidos/estado-fechas").requestUrl!!
            assertEquals("En preparación & +%", url.queryParameter("estado"))
            assertEquals("2026-11-14", url.queryParameter("desde"))
            assertEquals("2026-11-16", url.queryParameter("hasta"))
            assertEquals(wire, url.queryParameter("fechaBase"))
            assertEquals("2", url.queryParameter("pagina"))
        }
    }
    @Test fun locationPreservesAccentsAndOmitsAbsentFilters() = runBlocking {
        enqueue(pageJson())
        value(reportes.pedidosPorUbicacion(FiltroUbicacion(ciudad = "Bogotá & +%")))
        val url = take("GET", "/api/v1/reportes/pedidos/ubicacion").requestUrl!!
        assertEquals(setOf("ciudad", "pagina", "tamano"), url.queryParameterNames)
        assertEquals("Bogotá & +%", url.queryParameter("ciudad"))
        enqueue(pageJson())
        value(reportes.pedidosPorUbicacion(FiltroUbicacion("Colombia", "Bolívar", "Cartagena")))
        val all = take("GET", "/api/v1/reportes/pedidos/ubicacion").requestUrl!!
        assertEquals("Colombia", all.queryParameter("pais"))
        assertEquals("Bolívar", all.queryParameter("departamento"))
    }
    @Test fun nameReportMapsBothEnumsAndEncodesLiteralSearch() = runBlocking {
        for ((field, wireField) in listOf(CampoPerfil.NOMBRE to "nombre", CampoPerfil.USERNAME to "username")) {
            for ((match, wireMatch) in listOf(CoincidenciaPerfil.EXACTA to "exacta", CoincidenciaPerfil.PREFIJO to "prefijo")) {
                enqueue("[$PROFILE_JSON]")
                val profiles = value(reportes.perfilesPorNombreUsername(FiltroNombreUsername(field, "Á +_%&", match)))
                assertEquals(1, profiles.size)
                val url = take("GET", "/api/v1/reportes/usuarios/nombre-username").requestUrl!!
                assertEquals(setOf("campo", "termino", "coincidencia"), url.queryParameterNames)
                assertEquals(wireField, url.queryParameter("campo"))
                assertEquals(wireMatch, url.queryParameter("coincidencia"))
                assertEquals("Á +_%&", url.queryParameter("termino"))
            }
        }
    }
    @Test fun emailReportSupportsZeroOrOneAndEncodesPlus() = runBlocking {
        for (json in listOf("[]", "[$PROFILE_JSON]")) {
            enqueue(json)
            assertEquals(if (json == "[]") 0 else 1, value(reportes.perfilesPorCorreo("persona+tag@example.invalid")).size)
            val url = take("GET", "/api/v1/reportes/usuarios/correo").requestUrl!!
            assertEquals(setOf("correo"), url.queryParameterNames)
            assertEquals("persona+tag@example.invalid", url.queryParameter("correo"))
        }
    }
    @Test fun reportGet204IsInvalidForAllFourRoutes() = runBlocking {
        val calls: List<suspend () -> AppResult<*>> = listOf(
            { reportes.pedidosPorEstadoFechas(FiltroEstadoFechas()) },
            { reportes.pedidosPorUbicacion(FiltroUbicacion(pais = "Colombia")) },
            { reportes.perfilesPorNombreUsername(FiltroNombreUsername(CampoPerfil.NOMBRE, "Persona", CoincidenciaPerfil.EXACTA)) },
            { reportes.perfilesPorCorreo("persona@example.invalid") }
        )
        for (call in calls) {
            server.enqueue(MockResponse().setResponseCode(204))
            assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), call())
        }
    }
    @Test fun malformedMissingNullAndWrongTypeResponsesAreInvalid() = runBlocking {
        for (json in listOf("{", "null", "{}", ORDER_JSON.replace("2026-11-14", "2026-02-30"),
            ORDER_JSON.replace("1234.50", "\"1234.50\""), ORDER_JSON.replace("\"cliente\":\"Cliente Ejemplo\"", "\"cliente\":42"))) {
            enqueue(json)
            assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), pedidos.consultar(7))
        }
    }
    @Test fun arraysWithNullEntriesAndGlobalProfilesAreRejected() = runBlocking {
        enqueue("[null]")
        assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), pedidos.listar())
        for (json in listOf("[null]", "[$PROFILE_JSON,$PROFILE_JSON]")) {
            enqueue(json)
            assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), reportes.perfilesPorCorreo("persona@example.invalid"))
        }
    }
    @Test fun httpErrorsStayTypedAnd503DoesNotDestroyProvider() = runBlocking {
        val errors = listOf(400 to AppError.SolicitudInvalida, 401 to AppError.SesionNoValida, 403 to AppError.AccesoDenegado,
            503 to AppError.ServicioNoDisponible, 404 to AppError.ErrorHttp(404), 409 to AppError.ErrorHttp(409))
        for ((code, expected) in errors) {
            server.enqueue(MockResponse().setResponseCode(code))
            assertEquals(AppResult.Fallo(expected), usuarios.consultarPerfilPropio())
            take("GET", "/api/v1/usuarios/me")
        }
        enqueue(PROFILE_JSON)
        value(usuarios.consultarPerfilPropio())
        take("GET", "/api/v1/usuarios/me")
        Unit
    }
    @Test fun failedDeleteIsNotUnitSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(AppResult.Fallo(AppError.AccesoDenegado), usuarios.eliminarCuentaPropia())
    }
    @Test fun serviceUnavailableWithRetryAfterDoesNotRepeatWrite() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        enqueue(ORDER_JSON, 201)
        assertEquals(AppResult.Fallo(AppError.ServicioNoDisponible), pedidos.crear(datosPedido))
        assertEquals(1, server.requestCount)
    }
    @Test fun serviceUnavailableDoesNotRepeatAccountDeletion() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AppResult.Fallo(AppError.ServicioNoDisponible), usuarios.eliminarCuentaPropia())
        assertEquals(1, server.requestCount)
    }
    @Test fun disconnectedWriteIsNotRetriedAndReportsUncertainConnection() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(AppResult.Fallo(AppError.ErrorConexion), pedidos.crear(datosPedido))
        assertEquals(1, server.requestCount)
    }
    @Test fun recoverableProviderFailurePreventsHttpAndCanRecover() = runBlocking {
        var unavailable = true
        val executor = ApiRequestExecutor(AccessTokenProvider { if (unavailable) throw TokenUnavailableException() else "test-token-only" })
        val repository = HttpReportesRepository(reportesApi, executor)
        assertEquals(AppResult.Fallo(AppError.ServicioNoDisponible), repository.perfilesPorCorreo("persona@example.invalid"))
        assertEquals(0, server.requestCount)
        unavailable = false
        enqueue("[]")
        assertEquals(AppResult.Exito(emptyList<PerfilReporte>()), repository.perfilesPorCorreo("persona@example.invalid"))
    }
    @Test fun repositoryPropagatesCancellationFromProvider() = runBlocking {
        val executor = ApiRequestExecutor(AccessTokenProvider { throw CancellationException("test cancellation") })
        val repository = HttpReportesRepository(reportesApi, executor)
        try { repository.perfilesPorCorreo("persona@example.invalid"); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
        assertEquals(0, server.requestCount)
    }
    @Test fun containerComposesAllRepositoriesWithoutSessionOrNetwork() = runBlocking {
        val container = AppContainer("https://example.invalid/", AccessTokenProvider { null })
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), container.usuariosRepository.consultarPerfilPropio())
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), container.pedidosRepository.listar())
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), container.reportesRepository.perfilesPorCorreo("persona@example.invalid"))
        assertEquals(0, server.requestCount)
    }
}
