package com.neiderlizcano.gestorpedidos.nativo.domain.usuarios

data class Perfil(
    val id: String,
    val username: String,
    val nombre: String,
    val email: String
)

data class DatosPerfil(
    val username: String,
    val nombre: String
)
