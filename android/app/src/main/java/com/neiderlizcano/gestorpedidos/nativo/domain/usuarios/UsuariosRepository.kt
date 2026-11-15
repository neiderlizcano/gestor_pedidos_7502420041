package com.neiderlizcano.gestorpedidos.nativo.domain.usuarios

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult

/** Own profile only. eliminarCuentaPropia also removes the Auth account on the server. */
interface UsuariosRepository {
    suspend fun crearPerfil(datos: DatosPerfil): AppResult<Perfil>
    suspend fun consultarPerfilPropio(): AppResult<Perfil>
    suspend fun actualizarPerfilPropio(datos: DatosPerfil): AppResult<Perfil>
    suspend fun eliminarCuentaPropia(): AppResult<Unit>
}
