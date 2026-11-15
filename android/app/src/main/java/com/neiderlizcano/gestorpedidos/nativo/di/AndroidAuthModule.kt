package com.neiderlizcano.gestorpedidos.nativo.di

import android.content.Context
import com.neiderlizcano.gestorpedidos.nativo.core.session.AccessTokenProvider
import com.neiderlizcano.gestorpedidos.nativo.core.session.androidSessionStore
import com.neiderlizcano.gestorpedidos.nativo.data.auth.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.AuthRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.ResultadoAuth
import kotlinx.coroutines.*
import java.io.Closeable

// Una instancia por proceso, creada por el propietario de la aplicación cuando haya configuración aprobada.
class AndroidAuthModule(context: Context, authBaseUrl: String, publicKey: String, springBaseUrl: String = "") : Closeable, AuthRuntime, PedidosRuntime, UsuariosRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remote = AuthRemoteRest.create(authBaseUrl, publicKey)
    private val sessions = CoordinatedSessionManager(remote, androidSessionStore(context), scope)
    override val authRepository: AuthRepository = RestAuthRepository(remote, sessions)
    private val spring by lazy {
        if (springBaseUrl.isBlank()) null else try { backend(springBaseUrl) } catch (_: IllegalArgumentException) { null }
    }
    override val pedidosRepository by lazy { spring?.pedidosRepository ?: UnavailablePedidosRepository }
    override val usuariosRepository by lazy { spring?.usuariosRepository ?: UnavailableUsuariosRepository }
    val accessTokenProvider: AccessTokenProvider = SessionAccessTokenProvider(sessions)
    override suspend fun inicializar(): ResultadoAuth<Unit> = sessions.inicializar()
    fun backend(springBaseUrl: String): AppContainer = AppContainer(springBaseUrl, accessTokenProvider)
    // Liberar el módulo no equivale a cerrar sesión ni elimina la persistencia.
    override fun close() { scope.cancel() }
}
