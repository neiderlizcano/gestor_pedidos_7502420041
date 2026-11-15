package com.neiderlizcano.gestorpedidos.nativo.core.session

/**
 * Returns null for an absent/invalid session. Implementations translate expected temporary SDK
 * failures to TokenUnavailableException, and transport failures to IOException. Cancellation
 * and programming errors must propagate; no raw SDK messages or tokens belong in exceptions.
 */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

/** Temporary provider failure; deliberately retains neither a cause nor sensitive data. */
class TokenUnavailableException : Exception("Proveedor de sesión no disponible.")
