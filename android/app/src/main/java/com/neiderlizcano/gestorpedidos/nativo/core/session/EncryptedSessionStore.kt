package com.neiderlizcano.gestorpedidos.nativo.core.session

import com.google.gson.JsonParseException
import com.neiderlizcano.gestorpedidos.nativo.core.network.NetworkJson
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface SessionKeySource {
    fun obtenerParaCifrar(): SecretKey
    fun obtenerParaDescifrar(): SecretKey
}
internal interface SessionFile {
    fun leer(): ByteArray?
    fun escribir(bytes: ByteArray)
    fun invalidar()
    fun estaInvalidada(): Boolean
    fun habilitar()
    fun borrar()
}

internal class SessionCipher(private val keys: SessionKeySource) {
    private val aad = "gestor-pedidos-sesion-v1".toByteArray(Charsets.UTF_8)
    fun cifrar(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.obtenerParaCifrar())
        cipher.updateAAD(aad)
        val nonce = cipher.iv
        if (nonce.size != 12) throw GeneralSecurityException()
        return byteArrayOf(1) + nonce + cipher.doFinal(plain)
    }
    fun descifrar(bytes: ByteArray): ByteArray {
        if (bytes.size !in 30..65536 || bytes[0] != 1.toByte()) throw GeneralSecurityException()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keys.obtenerParaDescifrar(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(aad)
        return cipher.doFinal(bytes, 13, bytes.size - 13)
    }
}

internal class EncryptedSessionStore(private val file: SessionFile, private val cipher: SessionCipher) : SessionStore {
    private val mutex = Mutex()
    private val gson = NetworkJson.create()
    private class Wire(
        val version: Int?, val usuarioId: String?, val access: String?, val refresh: String?,
        val venceEn: String?, val pendiente: Boolean?
    )
    override suspend fun leer(): ResultadoAuth<SesionPersistida?> = operation {
        if (file.estaInvalidada()) return@operation null
        val bytes = file.leer() ?: return@operation null
        val plain = cipher.descifrar(bytes)
        try {
            val wire = gson.fromJson(String(plain, Charsets.UTF_8), Wire::class.java) ?: throw IOException()
            if (wire.version != 1 || wire.pendiente == null) throw IOException()
            val id = wire.usuarioId ?: throw IOException()
            val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { throw IOException() }
            val access = wire.access ?: throw IOException()
            val refresh = wire.refresh ?: throw IOException()
            if (id != canonical || access.isBlank() || access.any { it.code !in 33..126 } || refresh.isBlank()) throw IOException()
            SesionPersistida(SesionInterna(id, access, refresh, Instant.parse(wire.venceEn ?: throw IOException())),
                versionEsquema = 1, renovacionPendiente = wire.pendiente)
        } finally { plain.fill(0) }
    }
    override suspend fun sustituir(sesion: SesionPersistida): ResultadoAuth<Unit> = operation {
        if (sesion.versionEsquema != 1) throw IOException()
        val value = sesion.sesion
        val plain = gson.toJson(Wire(1, value.usuarioId, value.accessToken, value.refreshToken,
            value.venceEn.toString(), sesion.renovacionPendiente)).toByteArray(Charsets.UTF_8)
        try {
            val encrypted = cipher.cifrar(plain)
            if (encrypted.size > 65536) throw IOException()
            file.escribir(encrypted)
            file.habilitar()
        } finally { plain.fill(0) }
    }
    override suspend fun eliminar(): ResultadoAuth<Unit> = operation {
        // La marca durable impide restaurar credenciales si falla el borrado del archivo cifrado.
        file.invalidar()
        file.borrar()
    }
    private suspend fun <T> operation(block: () -> T): ResultadoAuth<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try { ResultadoAuth.Exito(block()) }
            catch (_: IOException) { ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) }
            catch (_: SecurityException) { ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) }
            catch (_: java.security.ProviderException) { ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) }
            catch (_: GeneralSecurityException) { ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) }
            catch (_: JsonParseException) { ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) }
            catch (_: DateTimeException) { ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO) }
        }
    }
}
