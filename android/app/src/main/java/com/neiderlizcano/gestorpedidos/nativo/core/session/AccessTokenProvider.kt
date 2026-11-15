package com.neiderlizcano.gestorpedidos.nativo.core.session

/**
 * Devuelve null si la sesión no existe o es inválida. Las implementaciones traducen los fallos
 * temporales previstos del SDK a TokenUnavailableException y los de transporte a IOException.
 * La cancelación y los errores de programación se propagan; las excepciones no incluyen tokens ni mensajes sin filtrar del SDK.
 */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

/** Fallo temporal del proveedor; no conserva la causa ni datos sensibles. */
class TokenUnavailableException : Exception("Proveedor de sesión no disponible.")
