package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.google.gson.JsonParseException
import com.neiderlizcano.gestorpedidos.nativo.core.network.*
import com.neiderlizcano.gestorpedidos.nativo.core.session.SesionInterna
import com.neiderlizcano.gestorpedidos.nativo.domain.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.util.Base64
import java.util.UUID

// Solo el transporte instrumentado puede asegurar que el refresh no llegó a enviarse.
internal class RefreshNotSentException : IOException("Refresh no enviado.")

internal interface AuthRemote {
    suspend fun signup(email: String, password: String): ResultadoAuth<SesionInterna?> = ResultadoAuth.Fallo(ErrorAuth.SERVICIO_NO_DISPONIBLE)
    suspend fun recover(email: String): ResultadoAuth<Unit>
    suspend fun verify(email: String, codigo: String): ResultadoAuth<SesionInterna>
    suspend fun updatePassword(sesion: SesionInterna, password: String): ResultadoAuth<Unit>
    suspend fun login(email: String, password: String): ResultadoAuth<SesionInterna>
    suspend fun refresh(refreshToken: String): ResultadoAuth<SesionInterna>
    suspend fun logout(accessToken: String): ResultadoAuth<Unit>
}

// La configuración se recibe desde fuera: no contiene valores del proyecto ni secretos de servidor.
internal class AuthRemoteRest(
    private val api: AuthApi,
    private val key: String,
    private val clock: Clock = Clock.systemUTC()
) : AuthRemote {
    override suspend fun signup(email: String, password: String): ResultadoAuth<SesionInterna?> = guarded {
        val response = api.signup(key, LoginDto(email, password))
        if (!response.isSuccessful) ResultadoAuth.Fallo(error(response, false))
        else {
            val dto = requiredField(response.body())
            if (dto.accessToken == null && dto.refreshToken == null) {
                val id = requiredField(dto.user?.id ?: dto.id)
                val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { throw InvalidPayloadException() }
                if (canonical != id) throw InvalidPayloadException()
                ResultadoAuth.Exito(null)
            } else ResultadoAuth.Exito(map(dto))
        }
    }
    override suspend fun login(email: String, password: String) = request { api.login(key, LoginDto(email, password)) }
    override suspend fun refresh(refreshToken: String): ResultadoAuth<SesionInterna> {
        val trace = RequestDispatchTrace()
        val result = request(refresh = true) { api.refresh(key, RefreshDto(refreshToken), trace) }
        if (result == ResultadoAuth.Fallo(ErrorAuth.CONEXION) && trace.noEnviada) throw RefreshNotSentException()
        return result
    }

    override suspend fun recover(email: String): ResultadoAuth<Unit> = guarded {
        val response = api.recover(key, EmailDto(email))
        when {
            response.code() == 200 -> ResultadoAuth.Exito(Unit)
            response.isSuccessful -> ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA)
            else -> ResultadoAuth.Fallo(error(response, false, true))
        }
    }
    override suspend fun verify(email: String, codigo: String) = request(recovery = true) { api.verify(key, OtpDto(email, codigo)) }
    override suspend fun updatePassword(sesion: SesionInterna, password: String): ResultadoAuth<Unit> = guarded {
        val response = api.updatePassword(key, "Bearer ${sesion.accessToken}", PasswordDto(password))
        when {
            !response.isSuccessful -> ResultadoAuth.Fallo(error(response, false, true))
            response.code() != 200 || response.body()?.id != sesion.usuarioId -> ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA)
            else -> ResultadoAuth.Exito(Unit)
        }
    }

    private suspend fun request(refresh: Boolean = false, recovery: Boolean = false, call: suspend () -> Response<SesionDto>): ResultadoAuth<SesionInterna> =
        guarded {
            val response = call()
            if (!response.isSuccessful) ResultadoAuth.Fallo(error(response, refresh, recovery))
            else if (response.code() != 200) ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA)
            else ResultadoAuth.Exito(map(requiredField(response.body())))
        }

    override suspend fun logout(accessToken: String): ResultadoAuth<Unit> = guarded {
        val response = api.logout(key, "Bearer $accessToken")
        if (response.code() == 204) ResultadoAuth.Exito(Unit)
        else if (response.isSuccessful) ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA)
        else ResultadoAuth.Fallo(error(response, refresh = false))
    }

    private fun map(dto: SesionDto): SesionInterna {
        val id = requiredField(requiredField(dto.user).id)
        val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { throw InvalidPayloadException() }
        if (id != canonical) throw InvalidPayloadException()
        val access = requiredField(dto.accessToken)
        val refresh = requiredField(dto.refreshToken)
        val lifetime = requiredField(dto.expiresIn)
        if (access.isBlank() || access.any { it.code !in 33..126 } || refresh.isBlank() ||
            dto.tokenType != "bearer" || lifetime <= 0) throw InvalidPayloadException()
        val expiry = try { dto.expiresAt?.let(Instant::ofEpochSecond) ?: clock.instant().plusSeconds(lifetime) }
            catch (_: DateTimeException) { throw InvalidPayloadException() }
            catch (_: ArithmeticException) { throw InvalidPayloadException() }
        if (!expiry.isAfter(clock.instant())) throw InvalidPayloadException()
        return SesionInterna(id, access, refresh, expiry)
    }

    private fun error(response: Response<*>, refresh: Boolean, recovery: Boolean = false): ErrorAuth {
        val body = response.errorBody()
        // No se retiene ni registra el cuerpo. Los errores grandes o no JSON se descartan.
        val code = body?.use {
            if (response.code() >= 500 || response.code() == 429 || it.contentType()?.subtype?.contains("json") != true) null
            else {
                val bytes = it.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (output.size() <= 8192) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                    }
                    if (output.size() > 8192) null else output.toByteArray()
                }
                try {
                    val json = bytes?.let { data -> NetworkJson.create().fromJson(String(data, Charsets.UTF_8), com.google.gson.JsonObject::class.java) }
                    json?.get("error_code")?.takeIf { value -> value.isJsonPrimitive && value.asJsonPrimitive.isString }?.asString
                } catch (_: JsonParseException) { null }
            }
        }
        return when {
            recovery && code in setOf("otp_expired", "otp_disabled") -> ErrorAuth.OTP_INVALIDO_O_EXPIRADO
            response.code() == 429 -> ErrorAuth.LIMITE_SOLICITUDES
            response.code() >= 500 -> ErrorAuth.SERVICIO_NO_DISPONIBLE
            refresh && code in setOf("refresh_token_not_found", "refresh_token_already_used", "session_not_found", "session_expired", "user_banned", "user_not_found") -> ErrorAuth.SESION_RECHAZADA
            !refresh && code == "invalid_credentials" -> ErrorAuth.CREDENCIALES_RECHAZADAS
            code in setOf("validation_failed", "email_address_invalid", "email_not_confirmed", "weak_password") -> ErrorAuth.ENTRADA_RECHAZADA
            else -> ErrorAuth.NO_CLASIFICADO
        }
    }

    private suspend fun <T> guarded(block: suspend () -> ResultadoAuth<T>): ResultadoAuth<T> = withContext(Dispatchers.IO) {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (_: InvalidPayloadException) { ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA) }
        catch (_: JsonParseException) { ResultadoAuth.Fallo(ErrorAuth.RESPUESTA_INVALIDA) }
        catch (_: IOException) { ResultadoAuth.Fallo(ErrorAuth.CONEXION) }
    }

    companion object {
        fun create(base: String, publicKey: String, clock: Clock = Clock.systemUTC()): AuthRemoteRest {
            val url = ApiBaseUrl(base)
            require(url.url.encodedPath == "/auth/v1/") { "La URL Auth debe terminar en /auth/v1/." }
            require(publicKey.all { it.code in 33..126 } && esPublica(publicKey)) { "Se requiere una clave pública apta para cliente." }
            return AuthRemoteRest(ApiNetwork.retrofit(url).create(AuthApi::class.java), publicKey, clock)
        }
        private fun esPublica(key: String): Boolean {
            if (key.startsWith("sb_publishable_") && key.length > "sb_publishable_".length) return true
            // Una anon heredada debe declarar ese rol; no se usa para validar identidad de usuarios.
            return try {
                val parts = key.split('.')
                if (parts.size != 3) false else {
                    val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                    NetworkJson.create().fromJson(payload, com.google.gson.JsonObject::class.java)?.get("role")?.asString == "anon"
                }
            } catch (_: IllegalArgumentException) { false }
                catch (_: JsonParseException) { false }
                catch (_: IllegalStateException) { false }
        }
    }
}
