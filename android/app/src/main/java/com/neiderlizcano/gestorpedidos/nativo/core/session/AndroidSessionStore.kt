package com.neiderlizcano.gestorpedidos.nativo.core.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.KeyStoreException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidSessionKeySource(private val alias: String = "gestor-pedidos-auth-v1") : SessionKeySource {
    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Synchronized override fun obtenerParaCifrar(): SecretKey {
        (keystore().getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }
    override fun obtenerParaDescifrar(): SecretKey =
        keystore().getKey(alias, null) as? SecretKey ?: throw KeyStoreException("Clave de sesión no disponible.")
}

internal class AndroidSessionFile(directory: File) : SessionFile {
    private val data = AtomicFile(File(directory, "sesion.enc"))
    private val invalid = AtomicFile(File(directory, "sesion.invalidada"))
    override fun leer(): ByteArray? {
        if (!data.baseFile.exists() && !File(data.baseFile.path + ".bak").exists()) return null
        return data.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                if (output.size() + n > 65536) throw IOException("Sobre de sesión inválido.")
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
    }
    override fun escribir(bytes: ByteArray) = write(data, bytes)
    override fun invalidar() = write(invalid, byteArrayOf(1))
    override fun estaInvalidada() = invalid.baseFile.exists() || File(invalid.baseFile.path + ".bak").exists()
    override fun habilitar() {
        invalid.delete()
        if (estaInvalidada()) throw IOException("No se pudo confirmar la sesión.")
    }
    override fun borrar() {
        data.delete()
        if (data.baseFile.exists() || File(data.baseFile.path + ".bak").exists()) throw IOException("No se pudo borrar la sesión.")
    }
    private fun write(file: AtomicFile, bytes: ByteArray) {
        var output: FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(bytes)
            output.fd.sync()
            file.finishWrite(output)
            // AtomicFile puede registrar ciertos fallos sin lanzarlos; comprueba el contenido confirmado.
            file.openRead().use { input ->
                for (byte in bytes) if (input.read() != (byte.toInt() and 255)) throw IOException("Escritura no confirmada.")
                if (input.read() != -1) throw IOException("Sobre de sesión inválido.")
            }
        } catch (e: IOException) {
            file.failWrite(output)
            throw e
        }
    }
}

internal fun androidSessionStore(context: Context): SessionStore = EncryptedSessionStore(
    AndroidSessionFile(File(context.applicationContext.noBackupFilesDir, "auth")),
    SessionCipher(AndroidSessionKeySource())
)
