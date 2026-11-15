package com.neiderlizcano.gestorpedidos.nativo.core.network

import com.google.gson.Gson
import com.google.gson.JsonParseException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object ApiNetwork {
    fun client(baseUrl: ApiBaseUrl): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(originGuard(baseUrl))
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // OkHttp 4.12 may follow 503 + Retry-After: 0 independently of the connection
            // retry flag. This API exposes errors to the caller instead of replaying writes.
            if (response.code == 503) response.newBuilder().removeHeader("Retry-After").build()
            else response
        }
        .build()

    private fun originGuard(baseUrl: ApiBaseUrl, checkBody: Boolean = false) = Interceptor { chain ->
        if (!baseUrl.permits(chain.request().url)) throw IOException("Origen API no permitido.")
        val response = chain.proceed(chain.request())
        if (checkBody) response.newBuilder().body(response.body?.let(::CheckedResponseBody)).build()
        else response
    }

    fun retrofit(baseUrl: ApiBaseUrl): Retrofit = retrofit(baseUrl, client(baseUrl))

    // Internal injection permits HTTPS certificates in local tests, not insecure production modes.
    internal fun retrofit(baseUrl: ApiBaseUrl, transport: OkHttpClient): Retrofit =
        Retrofit.Builder().baseUrl(baseUrl.url)
            .client(transport.newBuilder()
                .followRedirects(false).followSslRedirects(false)
                .addNetworkInterceptor(originGuard(baseUrl, checkBody = true)).build())
            .addConverterFactory(CheckedGsonConverter(NetworkJson.create()))
            .build()
}

private class CheckedGsonConverter(gson: Gson) : Converter.Factory() {
    private val delegate = GsonConverterFactory.create(gson)
    override fun responseBodyConverter(type: Type, annotations: Array<Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        val converter = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        return Converter<ResponseBody, Any?> { body ->
            try { converter.convert(body) }
            catch (_: JsonParseException) { throw InvalidPayloadException() }
            catch (_: IOException) { throw InvalidPayloadException() }
        }
    }
    override fun requestBodyConverter(type: Type, parameterAnnotations: Array<Annotation>, methodAnnotations: Array<Annotation>, retrofit: Retrofit) =
        delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)
}

// Retrofit can buffer the body before invoking a converter. Preserve the response-read phase.
private class CheckedResponseBody(private val delegate: ResponseBody) : ResponseBody() {
    private val checkedSource: BufferedSource by lazy {
        object : ForwardingSource(delegate.source()) {
            private var bytesRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = try { super.read(sink, byteCount) }
                    catch (_: IOException) { throw InvalidPayloadException() }
                if (count > 0) bytesRead += count
                val expected = this@CheckedResponseBody.delegate.contentLength()
                if (expected >= 0 && ((count == -1L && bytesRead != expected) || bytesRead > expected)) {
                    throw InvalidPayloadException()
                }
                return count
            }
        }.buffer()
    }
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun source() = checkedSource
}
