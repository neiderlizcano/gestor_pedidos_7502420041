package com.neiderlizcano.gestorpedidos.nativo.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Locale
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
        val gestor = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return ResultadoPosicion.SinProveedor
        val fino = concedido(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fino && !concedido(Manifest.permission.ACCESS_COARSE_LOCATION)) return ResultadoPosicion.SinProveedor
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
            ResultadoPosicion.SinProveedor
        }
    }

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
    private val idioma: Locale = Locale("es", "CO")
) : GeocodificadorInverso {
    private val app = context.applicationContext

    override suspend fun resolver(posicion: Posicion): ResultadoGeocodificacion {
        if (!Geocoder.isPresent()) return ResultadoGeocodificacion.ServicioNoDisponible
        val geocodificador = Geocoder(app, idioma)
        return try {
            val direcciones = buscar(geocodificador, posicion)
            val primera = direcciones.firstOrNull() ?: return ResultadoGeocodificacion.SinResultados
            ResultadoGeocodificacion.Exito(primera.aCruda())
        } catch (_: IOException) {
            // Sin Internet o sin acceso al servicio de direcciones del sistema.
            ResultadoGeocodificacion.SinConexion
        } catch (_: IllegalArgumentException) {
            ResultadoGeocodificacion.Fallo
        }
    }

    private suspend fun buscar(geocodificador: Geocoder, posicion: Posicion): List<Address> =
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuacion ->
                geocodificador.getFromLocation(posicion.latitud, posicion.longitud, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (continuacion.isActive) continuacion.resume(addresses) }
                    override fun onError(errorMessage: String?) {
                        if (continuacion.isActive) continuacion.resumeWith(Result.failure(IOException(errorMessage ?: "geocoder")))
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocodificador.getFromLocation(posicion.latitud, posicion.longitud, 1).orEmpty()
            }
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
