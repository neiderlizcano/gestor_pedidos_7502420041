package com.neiderlizcano.gestorpedidos.nativo.core.session

import com.neiderlizcano.gestorpedidos.nativo.domain.auth.ResultadoAuth
import java.time.Instant

// Clases sin toString generado para evitar que una excepción o depuración revele credenciales.
internal class SesionInterna(
    val usuarioId: String,
    val accessToken: String,
    val refreshToken: String,
    val venceEn: Instant
)
internal class SesionPersistida(
    val sesion: SesionInterna,
    val versionEsquema: Int = 1,
    val renovacionPendiente: Boolean = false
)
internal interface SessionStore {
    suspend fun leer(): ResultadoAuth<SesionPersistida?>
    suspend fun sustituir(sesion: SesionPersistida): ResultadoAuth<Unit>
    suspend fun eliminar(): ResultadoAuth<Unit>
}
