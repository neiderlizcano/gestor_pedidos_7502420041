package com.neiderlizcano.gestorpedidos.nativo.core.session

import com.neiderlizcano.gestorpedidos.nativo.domain.auth.ResultadoAuth
import java.time.Instant

/** Sesión con sus tokens. No es una clase de datos para que no tenga `toString` generado y ninguna excepción ni
 * traza de depuración revele credenciales. */
internal class SesionInterna(
    val usuarioId: String,
    val accessToken: String,
    val refreshToken: String,
    val venceEn: Instant
)
/** Sesión guardada junto con la versión del esquema y un indicador de renovación pendiente. */
internal class SesionPersistida(
    val sesion: SesionInterna,
    val versionEsquema: Int = 1,
    val renovacionPendiente: Boolean = false
)
/** Almacenamiento de la sesión: lectura, sustitución y eliminación con resultado tipado. */
internal interface SessionStore {
    suspend fun leer(): ResultadoAuth<SesionPersistida?>
    suspend fun sustituir(sesion: SesionPersistida): ResultadoAuth<Unit>
    suspend fun eliminar(): ResultadoAuth<Unit>
}
