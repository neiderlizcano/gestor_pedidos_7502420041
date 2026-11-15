package com.neiderlizcano.gestorpedidos.nativo.core.network

// Sin observación positiva del cliente, el resultado se considera incierto por defecto.
internal class RequestDispatchTrace {
    @Volatile var observado = false
    @Volatile var cabecerasIniciadas = false
    @Volatile var falloPrevioConfirmado = false
    val noEnviada: Boolean get() = observado && !cabecerasIniciadas && falloPrevioConfirmado
}
