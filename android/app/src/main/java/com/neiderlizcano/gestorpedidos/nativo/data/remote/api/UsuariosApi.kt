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

    // Intentionally no body, query, or Content-Type: the server rejects them for account deletion.
    @DELETE("api/v1/usuarios/me")
    suspend fun eliminar(@Header("Authorization") authorization: String): Response<Unit>
}
