package com.neiderlizcano.gestorpedidos.nativo.data.remote.api

import com.neiderlizcano.gestorpedidos.nativo.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ReportesApi {
    @GET("api/v1/reportes/pedidos/estado-fechas")
    suspend fun estadoFechas(
        @Header("Authorization") authorization: String,
        @Query("estado") estado: String?,
        @Query("desde") desde: String?,
        @Query("hasta") hasta: String?,
        @Query("fechaBase") fechaBase: String,
        @Query("pagina") pagina: Int,
        @Query("tamano") tamano: Int
    ): Response<PaginaPedidosDto>

    @GET("api/v1/reportes/pedidos/ubicacion")
    suspend fun ubicacion(
        @Header("Authorization") authorization: String,
        @Query("pais") pais: String?,
        @Query("departamento") departamento: String?,
        @Query("ciudad") ciudad: String?,
        @Query("pagina") pagina: Int,
        @Query("tamano") tamano: Int
    ): Response<PaginaPedidosDto>

    @GET("api/v1/reportes/usuarios/nombre-username")
    suspend fun nombreUsername(
        @Header("Authorization") authorization: String,
        @Query("campo") campo: String,
        @Query("termino") termino: String,
        @Query("coincidencia") coincidencia: String
    ): Response<List<PerfilDto?>>

    @GET("api/v1/reportes/usuarios/correo")
    suspend fun correo(@Header("Authorization") authorization: String, @Query("correo") correo: String): Response<List<PerfilDto?>>
}
