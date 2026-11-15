package com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper

import com.neiderlizcano.gestorpedidos.nativo.core.network.requiredField
import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.PedidoDto
import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.PedidoSolicitudDto
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.DatosPedido
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.Pedido
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.PedidoReporte

internal fun PedidoDto.toPedido(): Pedido = Pedido(
    id = requiredField(id),
    fecha = requiredField(fecha),
    fechaEnvio = requiredField(fechaEnvio),
    fechaEntrega = requiredField(fechaEntrega),
    cliente = requiredField(cliente),
    proveedor = requiredField(proveedor),
    valor = requiredField(valor),
    estado = requiredField(estado),
    pais = requiredField(pais),
    departamento = requiredField(departamento),
    ciudad = requiredField(ciudad),
    nomenclaturaVivienda = requiredField(nomenclaturaVivienda),
    propina = requiredField(propina)
)

internal fun PedidoDto.toReporte(): PedidoReporte = toPedido().let { pedido ->
    PedidoReporte(
        id = pedido.id,
        fecha = pedido.fecha,
        fechaEnvio = pedido.fechaEnvio,
        fechaEntrega = pedido.fechaEntrega,
        cliente = pedido.cliente,
        proveedor = pedido.proveedor,
        valor = pedido.valor,
        estado = pedido.estado,
        pais = pedido.pais,
        departamento = pedido.departamento,
        ciudad = pedido.ciudad,
        nomenclaturaVivienda = pedido.nomenclaturaVivienda,
        propina = pedido.propina
    )
}

internal fun DatosPedido.toSolicitud(): PedidoSolicitudDto = PedidoSolicitudDto(
    fecha = fecha,
    fechaEnvio = fechaEnvio,
    fechaEntrega = fechaEntrega,
    cliente = cliente,
    proveedor = proveedor,
    valor = valor,
    estado = estado,
    pais = pais,
    departamento = departamento,
    ciudad = ciudad,
    nomenclaturaVivienda = nomenclaturaVivienda,
    propina = propina
)
