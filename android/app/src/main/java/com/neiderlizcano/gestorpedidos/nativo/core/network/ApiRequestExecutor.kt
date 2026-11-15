package com.neiderlizcano.gestorpedidos.nativo.core.network

import com.google.gson.JsonParseException
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult
import com.neiderlizcano.gestorpedidos.nativo.core.session.AccessTokenProvider
import com.neiderlizcano.gestorpedidos.nativo.core.session.TokenUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.time.format.DateTimeParseException

// Reads a token per operation; only declared recoverable failures become application errors.
class ApiRequestExecutor(
    private val tokens: AccessTokenProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun <D : Any, T> execute(
        request: suspend (authorization: String) -> Response<D>,
        map: (D) -> T
    ): AppResult<T> = perform(request) { response ->
        val body = response.body() ?: throw InvalidPayloadException()
        map(body)
    }

    // Explicit opt-in for DELETE contracts. A body-required operation never uses this path.
    suspend fun executeEmpty(request: suspend (authorization: String) -> Response<Unit>): AppResult<Unit> =
        perform(request) { response ->
            if (response.code() != 204 && response.code() != 205) throw InvalidPayloadException()
            Unit
        }

    private suspend fun <D, T> perform(
        request: suspend (String) -> Response<D>,
        read: (Response<D>) -> T
    ): AppResult<T> = withContext(dispatcher) {
        try {
            val token = tokens.accessToken()
            if (token.isNullOrBlank() || token.any { it.code !in 33..126 }) {
                return@withContext AppResult.Fallo(AppError.SesionNoValida)
            }
            ensureActive()
            val response = request("Bearer $token")
            ensureActive()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext AppResult.Fallo(httpError(response.code()))
            }
            // Known format failures are recoverable only in the response-mapping phase.
            // In a token provider they could instead indicate a programming defect.
            val value = try { read(response) }
                catch (_: JsonParseException) { throw InvalidPayloadException() }
                catch (_: DateTimeParseException) { throw InvalidPayloadException() }
                catch (_: NumberFormatException) { throw InvalidPayloadException() }
            ensureActive()
            AppResult.Exito(value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: TokenUnavailableException) {
            ensureActive()
            AppResult.Fallo(AppError.ServicioNoDisponible)
        } catch (_: InvalidPayloadException) {
            ensureActive()
            AppResult.Fallo(AppError.RespuestaInvalida)
        } catch (_: IOException) {
            ensureActive()
            AppResult.Fallo(AppError.ErrorConexion)
        }
    }

    private fun httpError(code: Int): AppError = when (code) {
        400 -> AppError.SolicitudInvalida
        401 -> AppError.SesionNoValida
        403 -> AppError.AccesoDenegado
        503 -> AppError.ServicioNoDisponible
        else -> AppError.ErrorHttp(code)
    }
}
