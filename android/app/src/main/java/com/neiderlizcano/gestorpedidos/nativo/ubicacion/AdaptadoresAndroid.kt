package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// Adaptadores con servicios del sistema, sin Google Play Services ni servicios de pago:
// LocationManager (ubicación puntual) y android.location.Geocoder (geocodificación inversa).

/**
 * Elige el proveedor de ubicación disponible. Con permiso preciso se prefiere FUSED (API 31+), luego GPS y red;
 * con permiso aproximado solo se usan FUSED y red. Es una función pura para probarla sin dispositivo.
 */
internal fun elegirProveedor(habilitados: Set<String>, precisa: Boolean, sdk: Int): String? =
    proveedoresEnOrden(habilitados, precisa, sdk).firstOrNull()

/**
 * Todos los proveedores utilizables, del preferido al menos preferido. Si el primero no entrega posición
 * (por ejemplo, un proveedor FUSED sin datos) se prueba el siguiente en lugar de rendirse.
 */
internal fun proveedoresEnOrden(habilitados: Set<String>, precisa: Boolean, sdk: Int): List<String> {
    val orden = buildList {
        if (sdk >= 31) add("fused")
        if (precisa) add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
    }
    return orden.filter { it in habilitados }
}

class ProveedorPosicionAndroid(
    context: Context,
    private val tiempoLimiteMs: Long = 20_000
) : ProveedorPosicion {
    private val app = context.applicationContext

    private fun concedido(permiso: String) =
        ContextCompat.checkSelfPermission(app, permiso) == PackageManager.PERMISSION_GRANTED

    override suspend fun obtenerActual(precisa: Boolean): ResultadoPosicion {
        return try { obtenerConPermiso(precisa) } catch (_: SecurityException) { ResultadoPosicion.PermisoDenegado }
    }

    private suspend fun obtenerConPermiso(precisa: Boolean): ResultadoPosicion {
        val gestor = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return ResultadoPosicion.SinProveedor
        val fino = concedido(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fino && !concedido(Manifest.permission.ACCESS_COARSE_LOCATION)) return ResultadoPosicion.PermisoDenegado
        if (!LocationManagerCompat.isLocationEnabled(gestor)) return ResultadoPosicion.UbicacionDesactivada
        val proveedores = proveedoresEnOrden(gestor.getProviders(true).toSet(), precisa && fino, Build.VERSION.SDK_INT)
        if (proveedores.isEmpty()) return ResultadoPosicion.SinProveedor
        return try {
            // El tiempo límite cubre todo el intento; un proveedor que responde "sin posición" cede al siguiente.
            val ubicacion = withTimeout(tiempoLimiteMs) {
                var encontrada: Location? = null
                for (proveedor in proveedores) {
                    encontrada = ubicacionActual(gestor, proveedor)
                    if (encontrada != null) break
                }
                encontrada
            }
            if (ubicacion == null) ResultadoPosicion.Fallo
            else ResultadoPosicion.Exito(Posicion(ubicacion.latitude, ubicacion.longitude, ubicacion.accuracy.takeIf { ubicacion.hasAccuracy() }))
        } catch (_: TimeoutCancellationException) {
            ResultadoPosicion.TiempoAgotado
        } catch (_: SecurityException) {
            // El permiso pudo revocarse mientras se esperaba.
            ResultadoPosicion.PermisoDenegado
        }
    }

    /**
     * Pide una posición puntual al proveedor indicado. El único llamador, [obtenerConPermiso], comprueba el permiso
     * antes y captura [SecurityException] por si se revoca durante la espera; la anotación declara ese contrato.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun ubicacionActual(gestor: LocationManager, proveedor: String): Location? =
        suspendCancellableCoroutine { continuacion ->
            val senal = CancellationSignal()
            // Si se cancela la corrutina (botón Cancelar, salida de pantalla o tiempo límite) se detiene la búsqueda.
            continuacion.invokeOnCancellation { senal.cancel() }
            LocationManagerCompat.getCurrentLocation(gestor, proveedor, senal, ContextCompat.getMainExecutor(app)) { ubicacion ->
                if (continuacion.isActive) continuacion.resume(ubicacion)
            }
        }
}

class GeocodificadorAndroid(
    context: Context,
    private val idioma: Locale = Locale("es", "CO"),
    private val tiempoLimiteMs: Long = 15_000
) : GeocodificadorInverso {
    private val app = context.applicationContext

    override suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion =
        esperarGeocodificacion(tiempoLimiteMs) { responder ->
            if (!Geocoder.isPresent()) {
                responder(ResultadoGeocodificacion.ServicioNoDisponible)
                return@esperarGeocodificacion {}
            }
            val geocodificador = Geocoder(app, idioma)
            if (Build.VERSION.SDK_INT >= 33) {
                geocodificador.getFromLocation(posicion.latitud, posicion.longitud, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { responder(addresses.aResultado()) }
                    override fun onError(errorMessage: String?) {
                        // Android no identifica aquí una causa fiable; no se registra ni muestra el texto recibido.
                        responder(ResultadoGeocodificacion.Fallo)
                    }
                })
                // Esta API no ofrece cancelación del servicio; la espera descarta respuestas tardías.
                return@esperarGeocodificacion {}
            } else {
                consultarGeocodificacionBloqueante(ejecutorAntiguo, {
                    @Suppress("DEPRECATION")
                    geocodificador.getFromLocation(posicion.latitud, posicion.longitud, 1).orEmpty().aResultado()
                }, responder)
            }
        }

    private fun List<Address>.aResultado(): ResultadoGeocodificacion = firstOrNull()?.let {
        ResultadoGeocodificacion.Exito(it.aCruda())
    } ?: ResultadoGeocodificacion.SinResultados

    private companion object {
        // Un solo trabajo, sin cola: un servicio nativo bloqueado no acumula hilos al reintentar.
        val ejecutorAntiguo = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue(),
            { tarea -> Thread(tarea, "geocodificacion-puntual").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy())
    }

    private fun Address.aCruda(): DireccionCruda = DireccionCruda(
        pais = countryName,
        departamento = adminArea,
        subDepartamento = subAdminArea,
        localidad = locality,
        calle = thoroughfare,
        numero = subThoroughfare ?: numeroDesdeNombreDeLugar(featureName, thoroughfare, locality)
    )
}
