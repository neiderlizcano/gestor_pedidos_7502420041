package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.network.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthRemoteTest {
    private lateinit var server: MockWebServer
    private lateinit var remote: AuthRemoteRest
    private val clock = TestClock()
    private val key = "sb_publishable_test_only"
    private fun body() = """{"access_token":"access-test-only","refresh_token":"refresh-test-only","token_type":"bearer","expires_in":3600,"user":{"id":"$AUTH_USER"}}"""
    @Before fun prepare() {
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        val base = ApiBaseUrl(server.url("/auth/v1/").newBuilder().host("localhost").build().toString())
        val client = ApiNetwork.client(base).newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        remote = AuthRemoteRest(ApiNetwork.retrofit(base, client).create(AuthApi::class.java), key, clock)
    }
    @After fun release() { server.shutdown() }
    private fun enqueue(json: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json))
    }
    @Test fun loginUsesAuthOriginApiKeyAndNoUserBearer() = runBlocking {
        enqueue(body())
        val result = remote.login("persona@example.invalid", "test-password") as ResultadoAuth.Exito
        assertEquals("access-test-only", result.valor.accessToken)
        assertEquals(clock.instant().plusSeconds(3600), result.valor.venceEn)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/v1/token?grant_type=password", request.path)
        assertEquals(key, request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        val json = NetworkJson.create().fromJson(request.body.readUtf8(), com.google.gson.JsonObject::class.java)
        assertEquals(setOf("email", "password"), json.keySet())
        assertEquals("persona@example.invalid", json["email"].asString)
        assertEquals("test-password", json["password"].asString)
    }
    @Test fun refreshSendsOnlyRefreshTokenAndRotatesPair() = runBlocking {
        enqueue(body().replace("access-test-only", "new-access").replace("refresh-test-only", "new-refresh"))
        val result = remote.refresh("refresh-test-only") as ResultadoAuth.Exito
        assertEquals("new-access", result.valor.accessToken)
        assertEquals("new-refresh", result.valor.refreshToken)
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
        assertEquals(key, request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        val json = NetworkJson.create().fromJson(request.body.readUtf8(), com.google.gson.JsonObject::class.java)
        assertEquals(setOf("refresh_token"), json.keySet())
        assertEquals("refresh-test-only", json["refresh_token"].asString)
    }
    @Test fun explicitExpiryIsUsedWithoutFloatingPoint() = runBlocking {
        enqueue(body().replace("\"expires_in\":3600", "\"expires_in\":3600,\"expires_at\":${clock.instant().epochSecond + 7200}"))
        assertEquals(clock.instant().plusSeconds(7200), (remote.login("persona@example.invalid", "test-password") as ResultadoAuth.Exito).valor.venceEn)
    }
    @Test fun logoutUsesLocalScopeAndAcceptsOnly204() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertEquals(ResultadoAuth.Exito(Unit), remote.logout("access-test-only"))
        val request = server.takeRequest()
        assertEquals("/auth/v1/logout?scope=local", request.path)
        assertEquals("Bearer access-test-only", request.getHeader("Authorization"))
        assertEquals(key, request.getHeader("apikey"))
        assertEquals(0L, request.bodySize)
        assertNull(request.getHeader("Content-Type"))
        enqueue("{}")
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA), remote.logout("access-test-only"))
    }
    @Test fun invalidCredentialsClassificationUsesErrorCodeNotMessageOrStatus() = runBlocking {
        for (status in listOf(400, 422)) {
            enqueue("""{"error_code":"invalid_credentials","msg":"sin mostrar"}""", status)
            assertEquals(ResultadoAuth.Fallo(ErrorAuth.CREDENCIALES_RECHAZADAS), remote.login("persona@example.invalid", "test-password"))
        }
        enqueue("""{"msg":"invalid_credentials"}""", 400)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.NO_CLASIFICADO), remote.login("persona@example.invalid", "test-password"))
        enqueue("""{"error_code":"unexpected_code"}""", 401)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.NO_CLASIFICADO), remote.login("persona@example.invalid", "test-password"))
    }
    @Test fun recognizedRefreshRejectionIsDistinctFromTemporaryFailure() = runBlocking {
        enqueue("""{"error_code":"refresh_token_already_used"}""", 400)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.SESION_RECHAZADA), remote.refresh("refresh-test-only"))
        enqueue("""{"error_code":"unexpected_code"}""", 400)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.NO_CLASIFICADO), remote.refresh("refresh-test-only"))
    }
    @Test fun nonJson503And429DoNotExposeBodiesOrTriggerRetries() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setHeader("Content-Type", "text/html").setBody("<html>indisponible</html>"))
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE), remote.refresh("refresh-test-only"))
        assertEquals(1, server.requestCount)
        enqueue("{}", 429)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.LIMITE_SOLICITUDES), remote.login("persona@example.invalid", "test-password"))
    }
    @Test fun allRequiredSessionFieldsMustExistAndBeValid() = runBlocking {
        val json = NetworkJson.create().fromJson(body(), com.google.gson.JsonObject::class.java)
        for (field in json.keySet()) {
            val copy = json.deepCopy().apply { remove(field) }
            enqueue(copy.toString())
            assertEquals(ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA), remote.login("persona@example.invalid", "test-password"))
        }
        for (wire in listOf("{", "null", "{}", body().replace("3600", "0"), body().replace("3600", "1.5"),
            body().replace("bearer", "other"), body().replace(AUTH_USER, "invalid-id"))) {
            enqueue(wire)
            assertEquals(ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA), remote.login("persona@example.invalid", "test-password"))
        }
    }
    @Test fun redirectNeverForwardsCredentialsToAnotherOrigin() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", "https://example.invalid/collect"))
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.NO_CLASIFICADO), remote.login("persona@example.invalid", "test-password"))
        assertEquals(1, server.requestCount)
    }
    @Test fun disconnectedLoginDoesNotRetryPasswords() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), remote.login("persona@example.invalid", "test-password"))
        assertEquals(1, server.requestCount)
    }
    @Test fun configurationRejectsNonPublicKeysAndWrongOrigins() {
        for (url in listOf("http://example.invalid/auth/v1/", "https://example.invalid/", "https://example.invalid/auth/v1/?x=1")) {
            try { AuthRemoteRest.create(url, key); fail("URL no válida aceptada") } catch (_: IllegalArgumentException) { }
        }
        for (value in listOf("", "sb_secret_test_only", "service_role", "sb_publishable_test\r\nheader")) {
            try { AuthRemoteRest.create("https://example.invalid/auth/v1/", value); fail("Clave no pública aceptada") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun cancellationDuringHttpPropagates() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async { remote.login("persona@example.invalid", "test-password") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3, java.util.concurrent.TimeUnit.SECONDS)) }
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
    }
    @Test fun dnsFailureBeforeHeadersIsExplicitlyUnsent() = runBlocking {
        val base = ApiBaseUrl("https://example.invalid/auth/v1/")
        val client = ApiNetwork.client(base).newBuilder().dns(object : okhttp3.Dns { override fun lookup(hostname: String): List<java.net.InetAddress> = throw java.net.UnknownHostException() }).build()
        val transport = AuthRemoteRest(ApiNetwork.retrofit(base, client).create(AuthApi::class.java), key, clock)
        try { transport.refresh("refresh-test-only"); fail("Se esperaba refresh no enviado") }
        catch (_: RefreshNotSentException) { }
        assertEquals(0, server.requestCount)
    }
    @Test fun missingDispatchObserverMustRemainUncertain() = runBlocking {
        val base = ApiBaseUrl("https://example.invalid/auth/v1/")
        val client = ApiNetwork.client(base).newBuilder().eventListener(okhttp3.EventListener.NONE)
            .dns(object : okhttp3.Dns { override fun lookup(hostname: String): List<java.net.InetAddress> = throw java.net.UnknownHostException() }).build()
        val transport = AuthRemoteRest(ApiNetwork.retrofit(base, client).create(AuthApi::class.java), key, clock)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), transport.refresh("refresh-test-only"))
    }
    @Test fun lostResponseAfterRefreshSubmissionRemainsUncertain() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), remote.refresh("refresh-test-only"))
        assertEquals(1, server.requestCount)
    }

    @Test fun timeoutWithoutHeadersStillDoesNotProveNonSubmission() = runBlocking {
        val base = ApiBaseUrl("https://example.invalid/auth/v1/")
        val client = ApiNetwork.client(base).newBuilder().addInterceptor { throw java.net.SocketTimeoutException() }.build()
        val transport = AuthRemoteRest(ApiNetwork.retrofit(base, client).create(AuthApi::class.java), key, clock)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.CONEXION), transport.refresh("refresh-test-only"))
    }

    @Test fun recoveryRequestsUseExactBodiesAndNoNormalSessionBearer() = runBlocking {
        enqueue("{}")
        assertEquals(ResultadoAuth.Exito(Unit), remote.recover("persona@example.invalid"))
        val recover = server.takeRequest()
        assertEquals("/auth/v1/recover", recover.path)
        assertNull(recover.getHeader("Authorization"))
        assertEquals("sb_publishable_test_only", recover.getHeader("apikey"))
        val json = NetworkJson.create()
        assertEquals(setOf("email"), json.fromJson(recover.body.readUtf8(), com.google.gson.JsonObject::class.java).keySet())
        enqueue(body())
        assertTrue(remote.verify("persona@example.invalid", "test-code") is ResultadoAuth.Exito)
        val verify = server.takeRequest()
        assertEquals("/auth/v1/verify", verify.path)
        assertNull(verify.getHeader("Authorization"))
        val verification = json.fromJson(verify.body.readUtf8(), com.google.gson.JsonObject::class.java)
        assertEquals(setOf("email", "token", "type"), verification.keySet())
        assertEquals("recovery", verification["type"].asString)
        assertEquals("test-code", verification["token"].asString)
    }
    @Test fun passwordUpdateUsesOnlyRecoveryBearerAndPasswordField() = runBlocking {
        enqueue("""{"id":"$AUTH_USER"}""")
        assertEquals(ResultadoAuth.Exito(Unit), remote.updatePassword(session(clock, "recovery-access"), "test-password"))
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/auth/v1/user", request.path)
        assertEquals("Bearer recovery-access", request.getHeader("Authorization"))
        assertEquals(setOf("password"), NetworkJson.create().fromJson(request.body.readUtf8(), com.google.gson.JsonObject::class.java).keySet())
    }
    @Test fun expiredOtpAndRateLimitStayTyped() = runBlocking {
        enqueue("""{"error_code":"otp_expired"}""", 403)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.OTP_INVALIDO_O_EXPIRADO), remote.verify("persona@example.invalid", "test-code"))
        enqueue("{}", 429)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.LIMITE_SOLICITUDES), remote.recover("persona@example.invalid"))
    }

    @Test fun signupWithoutSessionUsesSeparateEndpointAndPublicKey() = runBlocking {
        enqueue("""{"id":"$AUTH_USER"}""")
        assertEquals(ResultadoAuth.Exito(null), remote.signup("persona@example.invalid", "test-password"))
        val request = server.takeRequest()
        assertEquals("POST", request.method); assertEquals("/auth/v1/signup", request.path)
        assertEquals(key, request.getHeader("apikey")); assertNull(request.getHeader("Authorization"))
    }
    @Test fun signupWithSessionAndMalformedResponseAreDistinguished() = runBlocking {
        enqueue(body())
        assertEquals("access-test-only", (remote.signup("persona@example.invalid", "test-password") as ResultadoAuth.Exito).valor!!.accessToken)
        enqueue("{}"); assertEquals(ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA), remote.signup("persona@example.invalid", "test-password"))
        enqueue("""{"id":"incorrecto"}""")
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA), remote.signup("persona@example.invalid", "test-password"))
    }

}
