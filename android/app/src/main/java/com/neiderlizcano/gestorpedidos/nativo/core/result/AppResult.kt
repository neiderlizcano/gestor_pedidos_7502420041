package com.neiderlizcano.gestorpedidos.nativo.core.result

sealed interface AppResult<out T> {
    data class Exito<T>(val valor: T) : AppResult<T>
    data class Fallo(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    data object SolicitudInvalida : AppError
    data object SesionNoValida : AppError
    data object AccesoDenegado : AppError
    data object ServicioNoDisponible : AppError
    data object ErrorConexion : AppError
    data object RespuestaInvalida : AppError
    data class ErrorHttp(val codigo: Int) : AppError
}
