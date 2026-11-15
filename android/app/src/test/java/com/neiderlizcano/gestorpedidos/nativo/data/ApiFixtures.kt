package com.neiderlizcano.gestorpedidos.nativo.data

internal const val PROFILE_JSON = """{"id":"00000000-0000-0000-0000-000000000001","username":"persona","nombre":"Persona Ejemplo","email":"persona@example.invalid"}"""
internal const val ORDER_JSON = """{"id":7,"fecha":"2026-11-14","fechaEnvio":"2026-11-15","fechaEntrega":"2026-11-16","cliente":"Cliente Ejemplo","proveedor":"Proveedor Ejemplo","valor":1234.50,"estado":"En preparación","pais":"Colombia","departamento":"Bolívar","ciudad":"Cartagena","nomenclaturaVivienda":"Dirección ficticia","propina":0.00}"""
internal fun pageJson(content: String = ORDER_JSON, page: Int = 0, next: Boolean = false) =
    """{"contenido":[$content],"pagina":$page,"tamano":20,"haySiguiente":$next}"""
