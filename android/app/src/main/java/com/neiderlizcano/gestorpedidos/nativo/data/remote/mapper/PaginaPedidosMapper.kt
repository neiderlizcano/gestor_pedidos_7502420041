package com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper

import com.neiderlizcano.gestorpedidos.nativo.core.network.InvalidPayloadException
import com.neiderlizcano.gestorpedidos.nativo.core.network.requiredField
import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.PaginaPedidosDto
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PaginaReporte
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte

internal fun PaginaPedidosDto.toPagina(): PaginaReporte<PedidoReporte> {
    val items = requiredField(contenido)
    val number = requiredField(pagina)
    val size = requiredField(tamano)
    // Response schema checks, not client-side authorization or request business rules.
    if (number < 0 || size !in 1..100 || items.size > size) throw InvalidPayloadException()
    return PaginaReporte(items.map { requiredField(it).toReporte() }, number, size, requiredField(haySiguiente))
}
