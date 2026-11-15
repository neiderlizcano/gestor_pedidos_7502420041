package com.neiderlizcano.gestorpedidos.nativo.domain.reportes

import java.math.BigDecimal
import java.time.LocalDate

// Shared SC-PROP-REPORTES contract; no UI or transport dependency.
data class FiltroEstadoFechas(
    val estado: String? = null,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val fechaBase: FechaBase = FechaBase.FECHA
)
data class FiltroUbicacion(val pais: String? = null, val departamento: String? = null, val ciudad: String? = null)
data class FiltroNombreUsername(val campo: CampoPerfil, val termino: String, val coincidencia: CoincidenciaPerfil)
enum class FechaBase { FECHA, FECHA_ENVIO, FECHA_ENTREGA }
enum class CampoPerfil { NOMBRE, USERNAME }
enum class CoincidenciaPerfil { EXACTA, PREFIJO }
data class SolicitudPagina(val pagina: Int = 0, val tamano: Int = 20)
data class PaginaReporte<T>(val contenido: List<T>, val pagina: Int, val tamano: Int, val haySiguiente: Boolean)

data class PedidoReporte(
    val id: Long,
    val fecha: LocalDate,
    val fechaEnvio: LocalDate,
    val fechaEntrega: LocalDate,
    val cliente: String,
    val proveedor: String,
    val valor: BigDecimal,
    val estado: String,
    val pais: String,
    val departamento: String,
    val ciudad: String,
    val nomenclaturaVivienda: String,
    val propina: BigDecimal
)

data class PerfilReporte(
    val id: String,
    val username: String,
    val nombre: String,
    val email: String
)
