package com.neiderlizcano.gestorpedidos.nativo.core.session

import com.neiderlizcano.gestorpedidos.nativo.data.auth.*
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.KeyStoreException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class MemorySessionFile : SessionFile {
    var bytes: ByteArray? = null
    var invalid = false
    var failWrite = false
    var failDelete = false
    override fun leer() = bytes?.clone()
    override fun escribir(bytes: ByteArray) { if (failWrite) throw IOException(); this.bytes = bytes.clone() }
    override fun invalidar() { invalid = true }
    override fun estaInvalidada() = invalid
    override fun habilitar() { invalid = false }
    override fun borrar() { if (failDelete) throw IOException(); bytes = null }
}
class EncryptedSessionStoreTest {
    private val file = MemorySessionFile()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val keys = object : SessionKeySource {
        override fun obtenerParaCifrar() = key
        override fun obtenerParaDescifrar() = key
    }
    private fun store() = EncryptedSessionStore(file, SessionCipher(keys))
    @Test fun roundTripAcrossStoreInstancesNeverWritesPlainTokens() = runBlocking {
        val original = session(TestClock())
        assertEquals(ResultadoAuth.Exito(Unit), store().sustituir(SesionPersistida(original)))
        val disk = String(file.bytes!!, Charsets.ISO_8859_1)
        assertFalse(disk.contains(original.accessToken)); assertFalse(disk.contains(original.refreshToken))
        val restored = (store().leer() as ResultadoAuth.Exito).valor!!
        assertEquals(original.usuarioId, restored.sesion.usuarioId)
        assertEquals(original.accessToken, restored.sesion.accessToken)
        assertEquals(original.refreshToken, restored.sesion.refreshToken)
        assertEquals(original.venceEn, restored.sesion.venceEn)
    }
    @Test fun encryptingSameSessionUsesDifferentNonce() = runBlocking {
        val store = store(); val session = SesionPersistida(session(TestClock()))
        store.sustituir(session); val first = file.bytes!!.clone()
        store.sustituir(session)
        assertFalse(first.contentEquals(file.bytes!!))
    }
    @Test fun tamperingIsRejectedWithoutLeakingPayload() = runBlocking {
        store().sustituir(SesionPersistida(session(TestClock())))
        val bytes = file.bytes!!; bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store().leer())
    }
    @Test fun missingKeyCannotSilentlyCreateReplacementDuringRead() = runBlocking {
        store().sustituir(SesionPersistida(session(TestClock())))
        val missing = object : SessionKeySource {
            override fun obtenerParaCifrar(): SecretKey = error("No debe generar clave")
            override fun obtenerParaDescifrar(): SecretKey = throw KeyStoreException()
        }
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), EncryptedSessionStore(file, SessionCipher(missing)).leer())
    }
    @Test fun pendingRotationSurvivesRestart() = runBlocking {
        store().sustituir(SesionPersistida(session(TestClock()), renovacionPendiente = true))
        assertTrue((store().leer() as ResultadoAuth.Exito).valor!!.renovacionPendiente)
    }
    @Test fun failedDeletionLeavesTombstoneThatPreventsRestore() = runBlocking {
        store().sustituir(SesionPersistida(session(TestClock())))
        file.failDelete = true
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store().eliminar())
        assertNotNull(file.bytes)
        assertEquals(ResultadoAuth.Exito(null), store().leer())
    }
    @Test fun freshLoginCanReplaceAnInvalidatedSession() = runBlocking {
        val store = store()
        store.sustituir(SesionPersistida(session(TestClock())))
        store.eliminar()
        assertEquals(ResultadoAuth.Exito(null), store.leer())
        store.sustituir(SesionPersistida(session(TestClock(), "new-access")))
        assertFalse(file.invalid)
        assertEquals("new-access", (store().leer() as ResultadoAuth.Exito).valor!!.sesion.accessToken)
    }
    @Test fun truncatedEnvelopeAndUnsupportedVersionAreStorageErrors() = runBlocking {
        for (bytes in listOf(byteArrayOf(1), ByteArray(40), ByteArray(65537))) {
            file.bytes = bytes
            assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store().leer())
        }
    }
    @Test fun interruptedWritePreservesPreviousCompleteEnvelope() = runBlocking {
        val store = store(); store.sustituir(SesionPersistida(session(TestClock())))
        file.failWrite = true
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store.sustituir(SesionPersistida(session(TestClock(), "new"))))
        assertEquals("access-test-only", (store().leer() as ResultadoAuth.Exito).valor!!.sesion.accessToken)
    }
    @Test fun absentFileIsDifferentFromCorruption() = runBlocking {
        assertEquals(ResultadoAuth.Exito(null), store().leer())
        file.bytes = byteArrayOf(0)
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store().leer())
    }
    @Test fun malformedEncryptedJsonAndSchemaAreNotAccepted() = runBlocking {
        for (plain in listOf("{", "null", "{}", "{\"version\":2}")) {
            file.bytes = SessionCipher(keys).cifrar(plain.toByteArray())
            assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store().leer())
        }
    }
    @Test fun secretContainersHaveNoGeneratedReadableTokenRepresentation() {
        val value = session(TestClock())
        assertFalse(value.toString().contains(value.accessToken))
        assertFalse(SesionPersistida(value).toString().contains(value.refreshToken))
    }
}
