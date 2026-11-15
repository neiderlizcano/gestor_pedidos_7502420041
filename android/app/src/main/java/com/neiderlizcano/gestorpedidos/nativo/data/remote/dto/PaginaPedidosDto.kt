package com.neiderlizcano.gestorpedidos.nativo.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PaginaPedidosDto(
    @SerializedName("contenido") val contenido: List<PedidoDto?>?,
    @SerializedName("pagina") val pagina: Int?,
    @SerializedName("tamano") val tamano: Int?,
    @SerializedName("haySiguiente") val haySiguiente: Boolean?
)
