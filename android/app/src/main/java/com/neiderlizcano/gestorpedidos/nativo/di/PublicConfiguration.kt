package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiBaseUrl
import com.neiderlizcano.gestorpedidos.nativo.data.auth.AuthRemoteRest

// Solo contiene nombres de propiedades y mensajes estáticos; nunca valores recibidos.
object PublicConfiguration {
    fun errors(authUrl: String, publicKey: String, apiUrl: String): List<String> = buildList {
        if (authUrl.isBlank()) add("Falta GESTOR_AUTH_URL.")
        else if (!validUrl(authUrl, "/auth/v1/")) add("GESTOR_AUTH_URL debe ser HTTPS y terminar exactamente en /auth/v1/, sin parámetros ni credenciales.")
        if (apiUrl.isBlank()) add("Falta GESTOR_API_URL.")
        else if (!validUrl(apiUrl, "/")) add("GESTOR_API_URL debe ser la raíz HTTPS de Render terminada en /, sin api/v1 ni parámetros o credenciales.")
        if (publicKey.isBlank()) add("Falta GESTOR_AUTH_PUBLIC_KEY.")
        else if (runCatching { AuthRemoteRest.create("https://configuracion.invalid/auth/v1/", publicKey) }.isFailure)
            add("GESTOR_AUTH_PUBLIC_KEY debe ser una clave pública publishable o anon válida; no se admiten claves privilegiadas.")
    }
    private fun validUrl(value: String, path: String): Boolean = runCatching {
        require(value == value.trim() && value.endsWith("/") && value.none { it.isWhitespace() || it.isISOControl() })
        require(ApiBaseUrl(value).url.encodedPath == path)
    }.isSuccess

    fun initialize(authUrl: String, publicKey: String, apiUrl: String,
        create: (String, String, String) -> AuthRuntime): AuthRuntime {
        val issues = errors(authUrl, publicKey, apiUrl)
        if (issues.isNotEmpty()) return UnavailableAuthRuntime(issues)
        return try { create(authUrl, publicKey, apiUrl) }
        catch (_: IllegalArgumentException) { UnavailableAuthRuntime(listOf("No se pudo inicializar la configuración pública. Revisa las propiedades y recompila la aplicación.")) }
    }
}
