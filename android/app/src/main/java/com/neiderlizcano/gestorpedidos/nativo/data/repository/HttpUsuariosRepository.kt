package com.neiderlizcano.gestorpedidos.nativo.data.repository

import com.neiderlizcano.gestorpedidos.nativo.core.network.ApiRequestExecutor
import com.neiderlizcano.gestorpedidos.nativo.data.remote.api.UsuariosApi
import com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper.toPerfil
import com.neiderlizcano.gestorpedidos.nativo.data.remote.mapper.toSolicitud
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.*

class HttpUsuariosRepository(private val api: UsuariosApi, private val executor: ApiRequestExecutor) : UsuariosRepository {
    override suspend fun crearPerfil(datos: DatosPerfil) = executor.execute({ api.crear(it, datos.toSolicitud()) }) { it.toPerfil() }
    override suspend fun consultarPerfilPropio() = executor.execute({ api.consultar(it) }) { it.toPerfil() }
    override suspend fun actualizarPerfilPropio(datos: DatosPerfil) = executor.execute({ api.actualizar(it, datos.toSolicitud()) }) { it.toPerfil() }
    override suspend fun eliminarCuentaPropia() = executor.executeEmpty { api.eliminar(it) }
}
