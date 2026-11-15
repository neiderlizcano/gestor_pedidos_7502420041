package com.neiderlizcano.gestorpedidos.nativo.core.network

import com.google.gson.JsonParseException
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppError
import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult
import com.neiderlizcano.gestorpedidos.nativo.core.session.SessionTokenProvider
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

// Obtiene el token en cada operación y convierte únicamente los fallos recuperables previstos.
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

    // Solo las consultas idempotentes optan por un único reintento tras renovar.
    suspend fun <D : Any, T> executeRead(
        request: suspend (authorization: String) -> Response<D>,
        map: (D) -> T
    ): AppResult<T> = perform(request, retryRead = true) { response ->
        map(response.body() ?: throw InvalidPayloadException())
    }

    // Variante para DELETE sin cuerpo de respuesta; las operaciones que requieren cuerpo usan execute.
    suspend fun executeEmpty(request: suspend (authorization: String) -> Response<Unit>): AppResult<Unit> =
        perform(request) { response ->
            if (response.code() != 204 && response.code() != 205) throw InvalidPayloadException()
            Unit
        }

    private suspend fun <D, T> perform(
        request: suspend (String) -> Response<D>,
        retryRead: Boolean = false,
        read: (Response<D>) -> T
    ): AppResult<T> = withContext(dispatcher) {
        try {
            val sessionTokens = tokens as? SessionTokenProvider
            var credential = sessionTokens?.credential()
            val token = if (sessionTokens != null) credential?.token else tokens.accessToken()
            if (token.isNullOrBlank() || token.any { it.code !in 33..126 }) {
                return@withContext AppResult.Fallo(AppError.SesionNoValida)
            }
            ensureActive()
            var response = request("Bearer $token")
            ensureActive()
            if (sessionTokens != null && credential != null) {
                if (!sessionTokens.isCurrent(credential)) {
                    response.errorBody()?.close()
                    return@withContext AppResult.Fallo(AppError.SesionNoValida)
                }
                if (response.code() == 401) {
                    response.errorBody()?.close()
                    val renewed = sessionTokens.refreshAfterUnauthorized(credential)
                        ?: return@withContext AppResult.Fallo(AppError.SesionNoValida)
                    // Las escrituras renuevan para operaciones futuras, pero no se vuelven a enviar.
                    if (!retryRead) return@withContext AppResult.Fallo(AppError.SesionNoValida)
                    if (!sessionTokens.isCurrent(renewed)) return@withContext AppResult.Fallo(AppError.SesionNoValida)
                    credential = renewed
                    response = request("Bearer ${renewed.token}")
                    ensureActive()
                    if (!sessionTokens.isCurrent(renewed)) {
                        response.errorBody()?.close()
                        return@withContext AppResult.Fallo(AppError.SesionNoValida)
                    }
                    if (response.code() == 401) {
                        response.errorBody()?.close()
                        sessionTokens.invalidateIfCurrent(renewed)
                        return@withContext AppResult.Fallo(AppError.SesionNoValida)
                    }
                }
            }
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                return@withContext AppResult.Fallo(httpError(response.code()))
            }
            // Los errores de formato conocidos se tratan como recuperables solo al mapear la respuesta.
            // En el proveedor de token podrían indicar un error de programación.
            val value = try { read(response) }
                catch (_: JsonParseException) { throw InvalidPayloadException() }
                catch (_: DateTimeParseException) { throw InvalidPayloadException() }
                catch (_: NumberFormatException) { throw InvalidPayloadException() }
            ensureActive()
            if (sessionTokens != null && credential != null && !sessionTokens.isCurrent(credential))
                return@withContext AppResult.Fallo(AppError.SesionNoValida)
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
