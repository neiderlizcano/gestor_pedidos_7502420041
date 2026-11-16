package com.neiderlizcano.gestorpedidos.nativo.core.result

/** Resultado de una operación remota: [Exito] con su valor o [Fallo] con un [AppError] tipado, sin excepciones ni texto de interfaz. */
sealed interface AppResult<out T> {
    data class Exito<T>(val valor: T) : AppResult<T>
    data class Fallo(val error: AppError) : AppResult<Nothing>
}

/** Errores de las operaciones HTTP, clasificados sin texto para el usuario; la interfaz los traduce a mensajes. */
sealed interface AppError {
    data object SolicitudInvalida : AppError
    data object SesionNoValida : AppError
    data object AccesoDenegado : AppError
    data object ServicioNoDisponible : AppError
    data object ErrorConexion : AppError
    data object RespuestaInvalida : AppError
    data class ErrorHttp(val codigo: Int) : AppError
}
