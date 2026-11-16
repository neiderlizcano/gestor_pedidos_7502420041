package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/** Propietario de ViewModels privado de una sesión; se limpia al cerrar sesión o cambiar de cuenta. */
internal class SessionViewModelOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
/** Ejecuta [content] con un almacén de ViewModels propio de la cuenta y de la versión de sesión, para que ningún estado pase de una cuenta a otra. */
@Composable
internal fun SessionContent(usuarioId: String, version: Long = 0, retained: RetainedSessionStores? = null, content: @Composable () -> Unit) {
    key(usuarioId, version) {
        val owner = if (retained != null) retained.obtain(usuarioId, version) else remember { SessionViewModelOwner() }
        DisposableEffect(owner) { onDispose { if (retained == null) owner.viewModelStore.clear() } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
    }
}

// El coordinador retenido conserva este propietario durante recreaciones de Activity.
internal class RetainedSessionStores {
    private var identity: Pair<String, Long>? = null
    private var owner: SessionViewModelOwner? = null
    fun obtain(user: String, version: Long): SessionViewModelOwner {
        if (identity != (user to version)) {
            clear()
            identity = user to version
            owner = SessionViewModelOwner()
        }
        return owner!!
    }
    fun clear() { owner?.viewModelStore?.clear(); owner = null; identity = null }
}
