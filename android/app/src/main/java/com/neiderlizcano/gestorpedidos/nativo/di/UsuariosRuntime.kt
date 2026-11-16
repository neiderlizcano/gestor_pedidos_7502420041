package com.neiderlizcano.gestorpedidos.nativo.di

import com.neiderlizcano.gestorpedidos.nativo.core.result.*
import com.neiderlizcano.gestorpedidos.nativo.domain.usuarios.*

/** Puerto que expone el repositorio de usuarios del contenedor de servicios. */
interface UsuariosRuntime { val usuariosRepository: UsuariosRepository }
/** Repositorio sin configuración: toda operación devuelve [AppError.ServicioNoDisponible]; nunca datos simulados. */
object UnavailableUsuariosRepository : UsuariosRepository {
    override suspend fun crearPerfil(datos: DatosPerfil) = unavailable<Perfil>()
    override suspend fun consultarPerfilPropio() = unavailable<Perfil>()
    override suspend fun actualizarPerfilPropio(datos: DatosPerfil) = unavailable<Perfil>()
    override suspend fun eliminarCuentaPropia() = unavailable<Unit>()
    private fun <T> unavailable(): AppResult<T> = AppResult.Fallo(AppError.ServicioNoDisponible)
}
