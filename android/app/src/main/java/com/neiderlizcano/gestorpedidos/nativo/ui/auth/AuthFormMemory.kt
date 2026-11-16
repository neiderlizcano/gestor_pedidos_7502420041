package com.neiderlizcano.gestorpedidos.nativo.ui.auth

import androidx.compose.runtime.mutableStateOf

// Estado efímero retenido durante rotación; no se serializa ni pertenece al estado público de sesión.
internal class AuthFormFields {
    val email = mutableStateOf("")
    val password = mutableStateOf("")
    val confirmation = mutableStateOf("")
    val code = mutableStateOf("")
    fun clear() { email.value = ""; password.value = ""; confirmation.value = ""; code.value = "" }
}
/** Conserva en memoria del ViewModel, nunca en Bundle ni en disco, los campos del formulario de acceso al rotar la pantalla. */
internal class AuthFormMemory {
    private var step: AuthStep? = null
    private val fields = AuthFormFields()
    fun obtain(current: AuthStep): AuthFormFields {
        if (step != current) { fields.clear(); step = current }
        return fields
    }
    fun clear() { fields.clear(); step = null }
}
