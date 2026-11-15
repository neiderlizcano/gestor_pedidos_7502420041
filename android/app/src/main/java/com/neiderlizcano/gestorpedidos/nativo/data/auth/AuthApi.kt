package com.neiderlizcano.gestorpedidos.nativo.data.auth

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

internal interface AuthApi {
    @POST("signup")
    suspend fun signup(@Header("apikey") key: String, @Body datos: LoginDto): Response<SesionDto>
    @POST("recover")
    suspend fun recover(@Header("apikey") key: String, @Body datos: EmailDto): Response<Unit>
    @POST("verify")
    suspend fun verify(@Header("apikey") key: String, @Body datos: OtpDto): Response<SesionDto>
    @PUT("user")
    suspend fun updatePassword(@Header("apikey") key: String, @Header("Authorization") bearer: String, @Body datos: PasswordDto): Response<UsuarioAuthDto>
    @POST("token?grant_type=password")
    suspend fun login(@Header("apikey") key: String, @Body datos: LoginDto): Response<SesionDto>
    @POST("token?grant_type=refresh_token")
    suspend fun refresh(@Header("apikey") key: String, @Body datos: RefreshDto, @Tag trace: com.neiderlizcano.gestorpedidos.nativo.core.network.RequestDispatchTrace): Response<SesionDto>
    @POST("logout?scope=local")
    suspend fun logout(@Header("apikey") key: String, @Header("Authorization") bearer: String): Response<Unit>
}
internal class LoginDto(val email: String, val password: String)
internal class RefreshDto(@SerializedName("refresh_token") val refreshToken: String)
internal class UsuarioAuthDto(val id: String?)
internal class SesionDto(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long?,
    @SerializedName("expires_at") val expiresAt: Long?,
    val user: UsuarioAuthDto?,
    val id: String? = null
)

internal class EmailDto(@SerializedName("email") val email: String)
internal class OtpDto(@SerializedName("email") val email: String, @SerializedName("token") val token: String) {
    @SerializedName("type") val type = "recovery"
}
internal class PasswordDto(@SerializedName("password") val password: String)
