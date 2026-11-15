package com.neiderlizcano.gestorpedidos.nativo.data.remote.api

import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface UsuariosApi {
    @POST("api/v1/usuarios")
    suspend fun crear(@Header("Authorization") authorization: String, @Body datos: PerfilSolicitudDto): Response<PerfilDto>

    @GET("api/v1/usuarios/me")
    suspend fun consultar(@Header("Authorization") authorization: String): Response<PerfilDto>

    @PUT("api/v1/usuarios/me")
    suspend fun actualizar(@Header("Authorization") authorization: String, @Body datos: PerfilSolicitudDto): Response<PerfilDto>

    // La eliminación de cuenta no envía cuerpo, parámetros ni Content-Type porque el servidor los rechaza.
    @DELETE("api/v1/usuarios/me")
    suspend fun eliminar(@Header("Authorization") authorization: String): Response<Unit>
}
