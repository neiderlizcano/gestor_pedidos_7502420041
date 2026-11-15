package com.neiderlizcano.gestorpedidos.nativo.data.remote.api

import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface PedidosApi {
    @POST("api/v1/pedidos")
    suspend fun crear(@Header("Authorization") authorization: String, @Body datos: PedidoSolicitudDto): Response<PedidoDto>

    @GET("api/v1/pedidos")
    suspend fun listar(@Header("Authorization") authorization: String): Response<List<PedidoDto?>>

    @GET("api/v1/pedidos/{id}")
    suspend fun consultar(@Header("Authorization") authorization: String, @Path("id") id: Long): Response<PedidoDto>

    @PUT("api/v1/pedidos/{id}")
    suspend fun actualizar(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body datos: PedidoSolicitudDto): Response<PedidoDto>

    @DELETE("api/v1/pedidos/{id}")
    suspend fun eliminar(@Header("Authorization") authorization: String, @Path("id") id: Long): Response<Unit>
}
