package com.neiderlizcano.gestorpedidos.nativo.core.session

/** Credencial de sesión opaca: no expone tokens a la interfaz ni genera una representación textual con secretos. */
class SessionCredential internal constructor(internal val token: String, internal val generation: Long)
/** Proveedor de credenciales para el cliente HTTP: entrega la vigente, renueva tras un 401 y permite invalidarla si sigue siendo la actual. */
interface SessionTokenProvider : AccessTokenProvider {
    suspend fun credential(): SessionCredential?
    suspend fun refreshAfterUnauthorized(rejected: SessionCredential): SessionCredential?
    suspend fun isCurrent(credential: SessionCredential): Boolean
    suspend fun invalidateIfCurrent(credential: SessionCredential)
}
