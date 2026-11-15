package com.neiderlizcano.gestorpedidos.nativo.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PerfilDto(
    @SerializedName("id") val id: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("nombre") val nombre: String?,
    @SerializedName("email") val email: String?
)

data class PerfilSolicitudDto(
    @SerializedName("username") val username: String,
    @SerializedName("nombre") val nombre: String
)
