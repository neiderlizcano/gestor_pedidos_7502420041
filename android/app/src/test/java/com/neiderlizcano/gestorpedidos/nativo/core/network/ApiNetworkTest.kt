package com.neiderlizcano.gestorpedidos.nativo.core.network

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult
import com.neiderlizcano.gestorpedidos.nativo.core.session.AccessTokenProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// Datos exclusivos de prueba, independientes de los DTO de negocio.
data class WirePayload(val label: String?, val amount: BigDecimal?, val date: LocalDate?)
interface ProbeApi {
    @DELETE("probe") suspend fun delete(@Header("Authorization") header: String): Response<Unit>
    @GET("probe") suspend fun read(@Header("Authorization") header: String): Response<WirePayload>
    @GET suspend fun absolute(@Url url: String, @Header("Authorization") header: String): Response<WirePayload>
}

class ApiNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ProbeApi
    private lateinit var transport: okhttp3.OkHttpClient
    private lateinit var base: ApiBaseUrl
    private val executor = ApiRequestExecutor(AccessTokenProvider { "test-token-only" })

    @Before fun setup() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        base = ApiBaseUrl(server.url("/").newBuilder().host("localhost").build().toString())
        transport = ApiNetwork.client(base).newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        api = ApiNetwork.retrofit(base, transport).create(ProbeApi::class.java)
    }

    @After fun teardown() { server.shutdown() }
    private fun valid() = MockResponse().setHeader("Content-Type", "application/json").setBody(
        """{"label":"sample","amount":1234567890.1234567890123456789,"date":"2026-11-14"}"""
    )
    private suspend fun read(): AppResult<String> = executor.execute({ api.read(it) }) { requiredField(it.label) }

    @Test fun delete204IsExplicitUnitSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AppResult.Exito(Unit), executor.executeEmpty { api.delete(it) })
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test fun emptyOperationRejectsUnexpected200() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), executor.executeEmpty { api.delete(it) })
    }

    @Test fun recoverableProviderFailureDoesNotInvalidateSessionOrSendRequest() = runBlocking {
        val unavailable = ApiRequestExecutor(AccessTokenProvider {
            throw com.neiderlizcano.gestorpedidos.nativo.core.session.TokenUnavailableException()
        })
        assertEquals(AppResult.Fallo(AppError.ServicioNoDisponible), unavailable.execute({ api.read(it) }) { it })
        assertEquals(0, server.requestCount)
    }

    @Test fun expectedMappingFormatFailureIsControlled() = runBlocking {
        server.enqueue(valid())
        assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), executor.execute({ api.read(it) }) {
            LocalDate.parse("invalid")
        })
    }

    @Test fun programmingErrorsAreNotHidden() = runBlocking {
        val broken = ApiRequestExecutor(AccessTokenProvider { error("programming bug") })
        try {
            broken.execute({ api.read(it) }) { it }
            fail("Programming exception was hidden")
        } catch (_: IllegalStateException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun mappingProgrammingErrorIsNotHidden() = runBlocking {
        server.enqueue(valid())
        try {
            executor.execute({ api.read(it) }) { error("mapping bug") }
            fail("Programming exception was hidden")
        } catch (_: IllegalStateException) { }
    }

    @Test fun providerProgrammingFormatErrorIsNotMistakenForInvalidResponse() = runBlocking {
        val broken = ApiRequestExecutor(AccessTokenProvider { throw NumberFormatException("provider bug") })
        try {
            broken.execute({ api.read(it) }) { it }
            fail("Programming exception was hidden")
        } catch (_: NumberFormatException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun providerTransportFailureIsControlled() = runBlocking {
        val unavailable = ApiRequestExecutor(AccessTokenProvider { throw IOException("test transport") })
        assertEquals(AppResult.Fallo(AppError.ErrorConexion), unavailable.execute({ api.read(it) }) { it })
        assertEquals(0, server.requestCount)
    }

    @Test fun localTlsHandshakeWorks() {
        server.enqueue(valid())
        transport.newCall(okhttp3.Request.Builder().url(base.url.resolve("probe")!!).build()).execute().use {
            assertEquals(200, it.code)
        }
    }

    @Test fun clientDisablesRedirectsAndRetries() {
        val c = ApiNetwork.client(base)
        assertFalse(c.followRedirects)
        assertFalse(c.followSslRedirects)
        assertFalse(c.retryOnConnectionFailure)
        assertEquals(30000, c.callTimeoutMillis)
    }

    @Test fun rejectsUnsafeBaseUrls() {
        for (url in listOf("http://localhost/", "https://user:pass@localhost/", "https://localhost/?q=a", "https://localhost/#x", "https://localhost/api")) {
            try { ApiBaseUrl(url); fail("Unsafe URL accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun successfulRequestUsesOnlyCurrentFakeToken() = runBlocking {
        server.enqueue(valid())
        assertEquals(AppResult.Exito("sample"), read())
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/probe", request.path)
        assertEquals("Bearer test-token-only", request.getHeader("Authorization"))
    }

    @Test fun missingTokenDoesNotMakeRequest() = runBlocking {
        val missing = ApiRequestExecutor(AccessTokenProvider { null })
        val result = missing.execute({ api.read(it) }) { requiredField(it.label) }
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), result)
        assertEquals(0, server.requestCount)
    }

    @Test fun invalidHeaderTokenDoesNotMakeRequest() = runBlocking {
        val missing = ApiRequestExecutor(AccessTokenProvider { "fake\r\nheader" })
        assertEquals(AppResult.Fallo(AppError.SesionNoValida), missing.execute({ api.read(it) }) { it })
        assertEquals(0, server.requestCount)
    }

    @Test fun tokenIsReadForEachOperation() = runBlocking {
        var calls = 0
        val changing = ApiRequestExecutor(AccessTokenProvider { calls++; "test-$calls" })
        repeat(2) {
            server.enqueue(valid())
            changing.execute({ api.read(it) }) { it }
        }
        assertEquals("Bearer test-1", server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        assertEquals("Bearer test-2", server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("Authorization"))
    }

    @Test fun httpErrorsMapWithoutErrorBodyOrLogout() = runBlocking {
        val cases = mapOf(400 to AppError.SolicitudInvalida, 401 to AppError.SesionNoValida,
            403 to AppError.AccesoDenegado, 503 to AppError.ServicioNoDisponible, 404 to AppError.ErrorHttp(404),
            500 to AppError.ErrorHttp(500))
        for ((code, expected) in cases) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("not-json-error"))
            assertEquals(AppResult.Fallo(expected), read())
        }
        server.enqueue(valid())
        assertEquals(AppResult.Exito("sample"), read())
    }

    @Test fun doesNotFollowCrossOriginOrDowngradeRedirects() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            for (destination in listOf(other.url("/leak").toString(),
                other.url("/leak").newBuilder().scheme("https").build().toString())) {
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination))
                assertEquals(AppResult.Fallo(AppError.ErrorHttp(302)), read())
                assertEquals(0, other.requestCount)
            }
        }
    }

    @Test fun rejectsDirectRequestsToOtherOriginsBeforeSendingToken() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            for (url in listOf(other.url("/leak"), other.url("/leak").newBuilder().scheme("https").build())) {
                val result = executor.execute({ api.absolute(url.toString(), it) }) { it }
                assertEquals(AppResult.Fallo(AppError.ErrorConexion), result)
            }
            assertEquals(0, other.requestCount)
        }
    }

    @Test fun malformedOrIncompleteJsonIsNotConnectionFailure() = runBlocking {
        for (body in listOf("{", """{"label":""", """{"label":"ok","date":"2026-02-30"}""")) {
            server.enqueue(MockResponse().setBody(body))
            assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), read())
        }
    }

    @Test fun absentRequiredFieldIsInvalid() = runBlocking {
        for (body in listOf("{}", """{"label":null}""")) {
            server.enqueue(MockResponse().setBody(body))
            assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), read())
        }
    }

    @Test fun noContentIsNotSuccessfulEmptyList() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), read())
    }

    @Test fun disconnectBeforeResponseIsTransportFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        assertEquals(AppResult.Fallo(AppError.ErrorConexion), read())
    }

    @Test fun truncatedResponseBodyIsInvalidRatherThanNoInternet() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"label\":\"sample\"}")
            .setHeader("Content-Length", "500")
            .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_END))
        assertEquals(AppResult.Fallo(AppError.RespuestaInvalida), read())
    }

    @Test fun cancellingInFlightHttpRequestDoesNotReturnNormalFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val pending = async { read() }
        try {
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
        } finally {
            pending.cancelAndJoin()
        }
        assertTrue(pending.isCancelled)
    }
    @Test fun transportFailureIsConnectionError() = runBlocking {
        val result = executor.execute<WirePayload, String>({ throw IOException("simulated transport") }) { requiredField(it.label) }
        assertEquals(AppResult.Fallo(AppError.ErrorConexion), result)
    }

    @Test fun cancellationPropagatesFromTokenProvider() = runBlocking {
        val cancelled = ApiRequestExecutor(AccessTokenProvider { throw CancellationException("test cancellation") })
        try { cancelled.execute({ api.read(it) }) { it }; fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellationPropagatesFromRequest() = runBlocking {
        try {
            executor.execute<WirePayload, WirePayload>({ throw CancellationException("test") }) { it }
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun exactDecimalsAndDatesAreDecodedOverHttps() = runBlocking {
        server.enqueue(valid())
        val result = executor.execute({ api.read(it) }) { requiredField(it.amount) to requiredField(it.date) }
        assertEquals(AppResult.Exito(BigDecimal("1234567890.1234567890123456789") to LocalDate.of(2026, 11, 14)), result)
    }
}
