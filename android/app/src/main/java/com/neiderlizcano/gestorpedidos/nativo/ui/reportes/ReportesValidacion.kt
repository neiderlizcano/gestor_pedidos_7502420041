package com.neiderlizcano.gestorpedidos.nativo.ui.reportes

import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CampoPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.CoincidenciaPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FechaBase
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroEstadoFechas
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroNombreUsername
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.FiltroUbicacion
import java.time.LocalDate
import java.time.format.DateTimeParseException

// Las reglas replican los límites del contrato SC-PROP-REPORTES para no enviar consultas que el
// servidor rechazaría. El servidor sigue siendo la autoridad: estas validaciones solo mejoran la experiencia.

/** Longitud máxima de `termino` en los reportes de nombre/username. */
internal const val LONGITUD_MAXIMA_TERMINO = 200

/** Longitud máxima de correo admitida por el reporte de correo. */
internal const val LONGITUD_MAXIMA_CORREO = 254

/** Tamaños de página ofrecidos al usuario; el servidor admite hasta 100. */
val TAMANOS_PAGINA: List<Int> = listOf(10, 20, 50)
const val TAMANO_PAGINA_PREDETERMINADO = 20

/** Campo del formulario al que se asocia un problema de validación. */
enum class ReporteCampo { ESTADO, DESDE, HASTA, UBICACION, TERMINO, CORREO }

/** Motivo de un problema de validación; la pantalla lo traduce a un texto de ayuda. */
enum class ReporteProblema { FECHA_ISO, RANGO_FECHAS, FILTRO_REQUERIDO, OBLIGATORIO, DEMASIADO_LARGO, CORREO_INVALIDO }

sealed interface ValidacionFiltro<out F> {
    data class Valido<F>(val filtro: F) : ValidacionFiltro<F>
    data class Invalido(val problemas: Map<ReporteCampo, ReporteProblema>) : ValidacionFiltro<Nothing>
}

/** Valores de texto tal como los escribe el usuario; nunca contienen identidad ni credenciales. */
data class FormularioEstadoFechas(
    val estado: String = "",
    val desde: String = "",
    val hasta: String = "",
    val fechaBase: FechaBase = FechaBase.FECHA,
    val tamano: Int = TAMANO_PAGINA_PREDETERMINADO
)

data class FormularioUbicacion(
    val pais: String = "",
    val departamento: String = "",
    val ciudad: String = "",
    val tamano: Int = TAMANO_PAGINA_PREDETERMINADO
)

data class FormularioNombreUsername(
    val campo: CampoPerfil = CampoPerfil.NOMBRE,
    val termino: String = "",
    val coincidencia: CoincidenciaPerfil = CoincidenciaPerfil.PREFIJO
)

data class FormularioCorreo(val correo: String = "")

private val FORMATO_FECHA = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val FORMATO_CORREO = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Interpreta una fecha ISO estricta (AAAA-MM-DD); devuelve null si no existe en el calendario. */
internal fun parsearFechaIso(texto: String): LocalDate? {
    if (!FORMATO_FECHA.matches(texto)) return null
    return try { LocalDate.parse(texto) } catch (_: DateTimeParseException) { null }
}

/** Un texto vacío o solo con espacios significa "sin filtro". */
private fun opcional(texto: String): String? = texto.trim().ifEmpty { null }

fun validarEstadoFechas(form: FormularioEstadoFechas): ValidacionFiltro<FiltroEstadoFechas> {
    val problemas = mutableMapOf<ReporteCampo, ReporteProblema>()
    val desdeTexto = opcional(form.desde)
    val hastaTexto = opcional(form.hasta)
    val desde = desdeTexto?.let { parsearFechaIso(it) ?: run { problemas[ReporteCampo.DESDE] = ReporteProblema.FECHA_ISO; null } }
    val hasta = hastaTexto?.let { parsearFechaIso(it) ?: run { problemas[ReporteCampo.HASTA] = ReporteProblema.FECHA_ISO; null } }
    if (desde != null && hasta != null && desde.isAfter(hasta)) problemas[ReporteCampo.DESDE] = ReporteProblema.RANGO_FECHAS
    if (problemas.isNotEmpty()) return ValidacionFiltro.Invalido(problemas)
    return ValidacionFiltro.Valido(FiltroEstadoFechas(opcional(form.estado), desde, hasta, form.fechaBase))
}

fun validarUbicacion(form: FormularioUbicacion): ValidacionFiltro<FiltroUbicacion> {
    val filtro = FiltroUbicacion(opcional(form.pais), opcional(form.departamento), opcional(form.ciudad))
    // Los filtros exactos se combinan con AND y al menos uno es obligatorio.
    if (filtro.pais == null && filtro.departamento == null && filtro.ciudad == null) {
        return ValidacionFiltro.Invalido(mapOf(ReporteCampo.UBICACION to ReporteProblema.FILTRO_REQUERIDO))
    }
    return ValidacionFiltro.Valido(filtro)
}

fun validarNombreUsername(form: FormularioNombreUsername): ValidacionFiltro<FiltroNombreUsername> {
    val termino = form.termino.trim()
    val problema = when {
        termino.isEmpty() -> ReporteProblema.OBLIGATORIO
        termino.length > LONGITUD_MAXIMA_TERMINO -> ReporteProblema.DEMASIADO_LARGO
        else -> null
    }
    if (problema != null) return ValidacionFiltro.Invalido(mapOf(ReporteCampo.TERMINO to problema))
    return ValidacionFiltro.Valido(FiltroNombreUsername(form.campo, termino, form.coincidencia))
}

fun validarCorreo(form: FormularioCorreo): ValidacionFiltro<String> {
    val correo = form.correo.trim()
    val problema = when {
        correo.isEmpty() -> ReporteProblema.OBLIGATORIO
        correo.length > LONGITUD_MAXIMA_CORREO -> ReporteProblema.DEMASIADO_LARGO
        !FORMATO_CORREO.matches(correo) -> ReporteProblema.CORREO_INVALIDO
        else -> null
    }
    if (problema != null) return ValidacionFiltro.Invalido(mapOf(ReporteCampo.CORREO to problema))
    return ValidacionFiltro.Valido(correo)
}
