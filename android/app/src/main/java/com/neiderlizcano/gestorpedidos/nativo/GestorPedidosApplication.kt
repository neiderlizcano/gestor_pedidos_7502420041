package com.neiderlizcano.gestorpedidos.nativo

import android.app.Application
import com.neiderlizcano.gestorpedidos.nativo.di.*

class GestorPedidosApplication : Application() {
    val auth: AuthRuntime by lazy {
        PublicConfiguration.initialize(BuildConfig.PUBLIC_AUTH_URL, BuildConfig.PUBLIC_AUTH_KEY, BuildConfig.PUBLIC_API_URL) { authUrl, key, apiUrl ->
            AndroidAuthModule(this, authUrl, key, apiUrl)
        }
    }
}
