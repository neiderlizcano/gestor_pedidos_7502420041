package com.neiderlizcano.gestorpedidos.nativo.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ApiBaseUrl(value: String) {
    val url: HttpUrl = (value.toHttpUrlOrNull() ?: throw IllegalArgumentException("URL API inválida.")).also {
        require(it.isHttps && it.username.isEmpty() && it.password.isEmpty() &&
            it.query == null && it.fragment == null && it.encodedPath.endsWith("/")) {
            "La base API debe ser HTTPS sin credenciales, query ni fragmento y terminar en /."
        }
    }

    internal fun permits(other: HttpUrl): Boolean =
        other.scheme == url.scheme && other.host == url.host && other.port == url.port
}
