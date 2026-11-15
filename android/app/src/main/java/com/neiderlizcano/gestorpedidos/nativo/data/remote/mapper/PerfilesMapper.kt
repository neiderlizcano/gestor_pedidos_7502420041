package com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper

import com.neiderlizcano.gestorpedidos.nativo.core.network.InvalidPayloadException
import com.neiderlizcano.gestorpedidos.nativo.core.network.requiredField
import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.PerfilDto
import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.PerfilSolicitudDto
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.DatosPerfil
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.Perfil
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PerfilReporte
import java.util.UUID

internal fun PerfilDto.toPerfil(): Perfil {
    val canonicalId = requiredField(id)
    val parsed = try { UUID.fromString(canonicalId) }
        catch (_: IllegalArgumentException) { throw InvalidPayloadException() }
    if (parsed.toString() != canonicalId) throw InvalidPayloadException()
    return Perfil(canonicalId, requiredField(username), requiredField(nombre), requiredField(email))
}

internal fun PerfilDto.toReporte(): PerfilReporte = toPerfil().let {
    PerfilReporte(it.id, it.username, it.nombre, it.email)
}
internal fun DatosPerfil.toSolicitud(): PerfilSolicitudDto = PerfilSolicitudDto(username, nombre)

// Rechaza respuestas con más de un perfil: el contrato del reporte permite cero o uno.
internal fun List<PerfilDto?>.toReportePerfiles(): List<PerfilReporte> {
    if (size > 1) throw InvalidPayloadException()
    return map { requiredField(it).toReporte() }
}
