package com.neiderlizcano.gestorpedidos.nativo.ubicacion

// Modelos y reglas de conversión de una dirección geocodificada a los cuatro campos del pedido.
// Es Kotlin puro (sin Android) para poder probarlo en la JVM. Regla de oro: nunca se inventa información;
// lo que el servicio no entrega queda vacío y se informa al usuario.

/** Campos de ubicación del pedido que este componente puede sugerir. */
enum class CampoDireccion { PAIS, DEPARTAMENTO, CIUDAD, NOMENCLATURA }

/** Observaciones que la interfaz traduce a mensajes en español. */
enum class AvisoDireccion {
    /** La ciudad se tomó de `subAdminArea` porque no hubo `locality`. */
    CIUDAD_APROXIMADA,
    /** Se obtuvo la calle/carrera pero no el número de vivienda. */
    SIN_NUMERO,
    /** El servicio dio un número sin calle: no se usa porque no identifica una vivienda. */
    NUMERO_SIN_CALLE,
    /** El usuario solo concedió ubicación aproximada: es poco probable que haya calle y número. */
    PRECISION_APROXIMADA
}

/** Campos crudos tal como los entrega `android.location.Address` (todos opcionales). */
data class DireccionCruda(
    val pais: String? = null,
    val departamento: String? = null,   // adminArea
    val subDepartamento: String? = null, // subAdminArea (municipio en algunos países)
    val localidad: String? = null,      // locality
    val calle: String? = null,          // thoroughfare
    val numero: String? = null          // subThoroughfare (o featureName con forma de número)
)

/**
 * Propuesta para el formulario. Un campo nulo significa "no se obtuvo": el formulario debe conservar
 * lo que el usuario ya tenga escrito en ese campo.
 */
data class DireccionSugerida(
    val pais: String?,
    val departamento: String?,
    val ciudad: String?,
    val nomenclatura: String?,
    val avisos: Set<AvisoDireccion>
) {
    /** Campos para los que el servicio no entregó nada utilizable. */
    val faltantes: Set<CampoDireccion>
        get() = buildSet {
            if (pais == null) add(CampoDireccion.PAIS)
            if (departamento == null) add(CampoDireccion.DEPARTAMENTO)
            if (ciudad == null) add(CampoDireccion.CIUDAD)
            if (nomenclatura == null) add(CampoDireccion.NOMENCLATURA)
        }

    /** Verdadero si no se obtuvo ningún dato. */
    val vacia: Boolean get() = faltantes.size == CampoDireccion.entries.size
}

private fun String?.limpio(): String? = this?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

/** Convierte lo geocodificado en una sugerencia sin rellenar ni suponer datos ausentes. */
fun DireccionCruda.aSugerencia(precisionAproximada: Boolean = false): DireccionSugerida {
    val avisos = mutableSetOf<AvisoDireccion>()
    if (precisionAproximada) avisos += AvisoDireccion.PRECISION_APROXIMADA

    val ciudadExacta = localidad.limpio()
    val ciudad = ciudadExacta ?: subDepartamento.limpio()?.also { avisos += AvisoDireccion.CIUDAD_APROXIMADA }

    val via = calle.limpio()
    val numeroLimpio = numero.limpio()?.removePrefix("#")?.trim()?.takeIf { it.isNotEmpty() }
    val nomenclatura = when {
        via != null && numeroLimpio != null ->
            // Algunos servicios ya incluyen el número dentro de la calle: no se duplica.
            if (via.contains(numeroLimpio)) via else "$via #$numeroLimpio"
        via != null -> { avisos += AvisoDireccion.SIN_NUMERO; via }
        numeroLimpio != null -> { avisos += AvisoDireccion.NUMERO_SIN_CALLE; null }
        else -> null
    }

    return DireccionSugerida(pais.limpio(), departamento.limpio(), ciudad, nomenclatura, avisos)
}

private val FORMA_NUMERO = Regex("^[#0-9A-Za-z]{0,6}[0-9][0-9A-Za-z #\\-]{0,12}$")

/**
 * `featureName` de Android a veces contiene el número de la vivienda y otras veces el nombre del lugar.
 * Solo se acepta como número si tiene forma de número (contiene un dígito, es corto y no repite la calle ni la ciudad).
 */
fun numeroDesdeNombreDeLugar(nombreDeLugar: String?, calle: String?, localidad: String?): String? {
    val candidato = nombreDeLugar.limpio() ?: return null
    if (!FORMA_NUMERO.matches(candidato)) return null
    if (candidato.equals(calle.limpio(), ignoreCase = true) || candidato.equals(localidad.limpio(), ignoreCase = true)) return null
    return candidato
}
