package com.neiderlizcano.gestorpedidos.nativo.ubicacion

// Puertos del componente: separan la lógica (probable en la JVM) de los servicios de Android.

/**
 * Posición puntual: este componente no la persiste ni realiza seguimiento continuo.
 * El geocodificador del sistema puede consultar un servicio externo para resolver la dirección.
 */
data class Posicion(val latitud: Double, val longitud: Double, val precisionMetros: Float? = null)

sealed interface ResultadoPosicion {
    data class Exito(val posicion: Posicion) : ResultadoPosicion
    /** El interruptor de ubicación del sistema (GPS) está apagado. */
    data object UbicacionDesactivada : ResultadoPosicion
    /** El permiso falta o se revocó mientras se obtenía la posición. */
    data object PermisoDenegado : ResultadoPosicion
    /** El dispositivo no tiene un proveedor de ubicación utilizable con los permisos concedidos. */
    data object SinProveedor : ResultadoPosicion
    data object TiempoAgotado : ResultadoPosicion
    data object Fallo : ResultadoPosicion
}

/** Obtiene una única posición actual. Debe ser cancelable: al cancelar la corrutina se detiene la búsqueda. */
interface ProveedorPosicion {
    suspend fun obtenerActual(precisa: Boolean): ResultadoPosicion
}

sealed interface ResultadoGeocodificacion {
    data class Exito(val direccion: DireccionCruda) : ResultadoGeocodificacion
    data object SinResultados : ResultadoGeocodificacion
    data object TiempoAgotado : ResultadoGeocodificacion
    /** Ausencia de conexión acreditada por el proveedor; un error genérico del geocodificador no basta. */
    data object SinConexion : ResultadoGeocodificacion
    /** El dispositivo no incluye un servicio de geocodificación. */
    data object ServicioNoDisponible : ResultadoGeocodificacion
    data object Fallo : ResultadoGeocodificacion
}

interface GeocodificadorInverso {
    suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion
}
