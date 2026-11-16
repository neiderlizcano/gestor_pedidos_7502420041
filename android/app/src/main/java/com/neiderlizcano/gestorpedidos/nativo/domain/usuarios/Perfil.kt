package com.neiderlizcano.gestorpedidos.nativo.domain.usuarios

/** Perfil propio del usuario autenticado; el correo es de solo lectura. */
data class Perfil(
    val id: String,
    val username: String,
    val nombre: String,
    val email: String
)

/** Campos editables del perfil: nombre de usuario y nombre. */
data class DatosPerfil(
    val username: String,
    val nombre: String
)
