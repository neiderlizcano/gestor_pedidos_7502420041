package com.neiderlizcano.gestorpedidos.nativo.core.session

// La credencial no expone tokens al estado de interfaz ni genera una representación con secretos.
class SessionCredential internal constructor(internal val token: String, internal val generation: Long)
interface SessionTokenProvider : AccessTokenProvider {
    suspend fun credential(): SessionCredential?
    suspend fun refreshAfterUnauthorized(rejected: SessionCredential): SessionCredential?
    suspend fun isCurrent(credential: SessionCredential): Boolean
    suspend fun invalidateIfCurrent(credential: SessionCredential)
}
