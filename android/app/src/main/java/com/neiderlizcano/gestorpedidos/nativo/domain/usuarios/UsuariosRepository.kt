package com.neiderlizcano.gestorpedidos.nativo.domain.usuarios

import com.neiderlizcano.gestorpedidos.nativo.core.result.AppResult

/** Solo permite operar sobre el perfil propio. eliminarCuentaPropia también elimina la cuenta Auth en el servidor. */
interface UsuariosRepository {
    suspend fun crearPerfil(datos: DatosPerfil): AppResult<Perfil>
    suspend fun consultarPerfilPropio(): AppResult<Perfil>
    suspend fun actualizarPerfilPropio(datos: DatosPerfil): AppResult<Perfil>
    suspend fun eliminarCuentaPropia(): AppResult<Unit>
}
