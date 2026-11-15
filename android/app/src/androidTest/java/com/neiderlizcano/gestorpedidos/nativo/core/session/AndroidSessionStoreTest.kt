package com.neiderlizcano.gestorpedidos.nativo.core.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidSessionStoreTest {
    private lateinit var directory: File
    private lateinit var alias: String
    private fun store() = EncryptedSessionStore(AndroidSessionFile(directory), SessionCipher(AndroidSessionKeySource(alias)))
    private fun sample() = SesionPersistida(SesionInterna(
        "00000000-0000-0000-0000-000000000001", "instrumented-access-test", "instrumented-refresh-test", Instant.now().plusSeconds(3600)))
    @Before fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        directory = File(context.noBackupFilesDir, "auth-test-$id")
        alias = "gestor-pedidos-test-$id"
    }
    @After fun release() {
        // Solo se eliminan los recursos únicos creados por esta prueba.
        check(directory.name.startsWith("auth-test-") && alias.startsWith("gestor-pedidos-test-"))
        directory.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
    @Test fun keystoreKeyIsNonExportableAndSessionSurvivesStoreRecreation() = runBlocking {
        val sample = sample()
        assertEquals(ResultadoAuth.Exito(Unit), store().sustituir(sample))
        assertNull(AndroidSessionKeySource(alias).obtenerParaDescifrar().encoded)
        val restored = (store().leer() as ResultadoAuth.Exito).valor!!
        assertEquals(sample.sesion.accessToken, restored.sesion.accessToken)
        assertEquals(sample.sesion.refreshToken, restored.sesion.refreshToken)
        val encrypted = File(directory, "sesion.enc").readBytes()
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains(sample.sesion.accessToken))
        assertTrue(directory.canonicalPath.startsWith(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir.canonicalPath + File.separator))
    }
    @Test fun invalidatedKeyIsStorageErrorNotEmptySession() = runBlocking {
        store().sustituir(sample())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        assertEquals(ResultadoAuth.Fallo(ErrorAuth.ALMACENAMIENTO), store().leer())
    }
    @Test fun interruptedAtomicWriteRestoresPreviousCompleteCiphertext() = runBlocking {
        store().sustituir(sample())
        val atomic = android.util.AtomicFile(File(directory, "sesion.enc"))
        val output = atomic.startWrite()
        output.write(byteArrayOf(1, 2, 3))
        atomic.failWrite(output)
        assertTrue(store().leer() is ResultadoAuth.Exito)
        assertEquals(ResultadoAuth.Exito(Unit), store().eliminar())
        assertEquals(ResultadoAuth.Exito(null), store().leer())
    }
}
