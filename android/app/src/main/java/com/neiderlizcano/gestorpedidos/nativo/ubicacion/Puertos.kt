package com.neiderlizcano.gestorpedidos.nativo.ubicacion

// Puertos del componente: separan la lógica (probable en la JVM) de los servicios de Android.

/** Posición puntual. No se guarda, no se registra y no sale del dispositivo: solo alimenta la geocodificación. */
data class Posicion(val latitud: Double, val longitud: Double, val precisionMetros: Float? = null)

sealed interface ResultadoPosicion {
    data class Exito(val posicion: Posicion) : ResultadoPosicion
    /** El interruptor de ubicación del sistema (GPS) está apagado. */
    data object UbicacionDesactivada : ResultadoPosicion
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
    /** Falló la consulta de red al servicio de direcciones (sin Internet o servicio inaccesible). */
    data object SinConexion : ResultadoGeocodificacion
    /** El dispositivo no incluye un servicio de geocodificación. */
    data object ServicioNoDisponible : ResultadoGeocodificacion
    data object Fallo : ResultadoGeocodificacion
}

interface GeocodificadorInverso {
    suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion
}
