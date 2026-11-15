package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.neiderlizcano.gestorpedidos.nativo.core.network.*
import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.ReportesApi
import com.neiderlizcano.gestorpedidos.nativo.data.repository.HttpReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class AuthToSpringIntegrationTest {
    @Test fun loginRotationAndLogoutKeepAuthCredentialsOutOfSpring() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val authServer = MockWebServer(); val springServer = MockWebServer()
        for (server in listOf(authServer, springServer)) { server.useHttps(tls.sslSocketFactory(), false); server.start() }
        try {
            fun retrofit(server: MockWebServer, path: String): retrofit2.Retrofit {
                val base = ApiBaseUrl(server.url(path).newBuilder().host("localhost").build().toString())
                val client = ApiNetwork.client(base).newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
                return ApiNetwork.retrofit(base, client)
            }
            val clock = TestClock()
            val remote = AuthRemoteRest(retrofit(authServer, "/auth/v1/").create(AuthApi::class.java), "sb_publishable_test_only", clock)
            val sessions = CoordinatedSessionManager(remote, MemoryStore(), scope, clock)
            val auth = RestAuthRepository(remote, sessions)
            val reports = HttpReportesRepository(retrofit(springServer, "/").create(ReportesApi::class.java),
                ApiRequestExecutor(SessionAccessTokenProvider(sessions)))
            fun authResponse(access: String, refresh: String, seconds: Int) = MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"access_token":"$access","refresh_token":"$refresh","token_type":"bearer","expires_in":$seconds,"user":{"id":"$AUTH_USER"}}""")
            authServer.enqueue(authResponse("first-access", "first-refresh", 61))
            assertEquals(ResultadoAuth.Exito(Unit), auth.iniciarSesion("persona@example.invalid", "test-password"))
            for (token in listOf("first-access", "rotated-access")) {
                if (token == "rotated-access") {
                    clock.now = clock.now.plusSeconds(2)
                    authServer.enqueue(authResponse("rotated-access", "rotated-refresh", 3600))
                }
                springServer.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("[]"))
                assertTrue(reports.perfilesPorCorreo("persona@example.invalid") is AppResult.Exito)
                val request = springServer.takeRequest()
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertNull(request.getHeader("apikey"))
                assertEquals(0L, request.bodySize)
            }
            val login = authServer.takeRequest(); val refresh = authServer.takeRequest()
            assertNull(login.getHeader("Authorization")); assertNull(refresh.getHeader("Authorization"))
            assertEquals("sb_publishable_test_only", login.getHeader("apikey"))
            assertEquals("/auth/v1/token?grant_type=refresh_token", refresh.path)
            assertTrue(refresh.body.readUtf8().contains("first-refresh"))
            authServer.enqueue(MockResponse().setResponseCode(204))
            assertEquals(ResultadoAuth.Exito(ResultadoLogout(RevocacionRemota.CONFIRMADA)), auth.cerrarSesion())
            assertEquals("Bearer rotated-access", authServer.takeRequest().getHeader("Authorization"))
            assertEquals(AppResult.Fallo(AppError.SesionNoValida), reports.perfilesPorCorreo("persona@example.invalid"))
            assertEquals(2, springServer.requestCount)
            assertEquals(3, authServer.requestCount)
        } finally {
            scope.cancel(); authServer.shutdown(); springServer.shutdown()
        }
    }
}
