package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiBaseUrl
import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiNetwork
import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiRequestExecutor
import com.neiderlizcano.gestorpedidos.nativo.core.session.AccessTokenProvider
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.PedidosApi
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.ReportesApi
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.UsuariosApi
import com.neiderlizcano.gestorpedidos.nativo.data.repository.HttpPedidosRepository
import com.neiderlizcano.gestorpedidos.nativo.data.repository.HttpReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.data.repository.HttpUsuariosRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.pedidos.PedidosRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.reportes.ReportesRepository
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.UsuariosRepository

/** Construye las dependencias con la URL HTTPS aprobada y el proveedor del token actual. */
class AppContainer(baseUrl: String, tokens: AccessTokenProvider) {
    private val apiBaseUrl = ApiBaseUrl(baseUrl)
    private val httpClient = ApiNetwork.client(apiBaseUrl)
    private val retrofit = ApiNetwork.retrofit(apiBaseUrl, httpClient)
    private val executor = ApiRequestExecutor(tokens)
    private val usuariosApi: UsuariosApi = retrofit.create(UsuariosApi::class.java)
    private val pedidosApi: PedidosApi = retrofit.create(PedidosApi::class.java)
    private val reportesApi: ReportesApi = retrofit.create(ReportesApi::class.java)
    val usuariosRepository: UsuariosRepository = HttpUsuariosRepository(usuariosApi, executor)
    val pedidosRepository: PedidosRepository = HttpPedidosRepository(pedidosApi, executor)
    val reportesRepository: ReportesRepository = HttpReportesRepository(reportesApi, executor)
}
