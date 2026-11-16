package com.neiderlizcano.gestorpedidos.nativo.sensores

/** Hardware simulado: registra altas y bajas y permite emitir lecturas a mano. */
internal class FakeSensorHardware(
    var sensores: Map<TipoSensor, InfoSensor> = mapOf(
        TipoSensor.ACELEROMETRO to InfoSensor("Acelerómetro de prueba", 78.4f),
        TipoSensor.PROXIMIDAD to InfoSensor("Proximidad de prueba", 5f)
    )
) : SensorHardware {
    var aceptaRegistro = true
    var lanzaAlRegistrar = false
    var registros = 0
    var liberaciones = 0
    private val activas = mutableListOf<Pair<TipoSensor, EscuchaSensor>>()

    val suscripcionesActivas: Int get() = activas.size

    override fun informacion(tipo: TipoSensor): InfoSensor? = sensores[tipo]

    override fun registrar(tipo: TipoSensor, escucha: EscuchaSensor): Boolean {
        if (lanzaAlRegistrar) throw IllegalStateException("fallo simulado")
        if (!aceptaRegistro) return false
        registros++
        activas += tipo to escucha
        return true
    }

    override fun liberar(escucha: EscuchaSensor) {
        if (activas.removeAll { it.second === escucha }) liberaciones++
    }

    fun emitir(tipo: TipoSensor, vararg valores: Float, precision: Int = 3, instanteNs: Long = 0L) {
        activas.filter { it.first == tipo }.forEach { it.second.alRecibir(valores.copyOf(), precision, instanteNs) }
    }
}

internal fun ms(milisegundos: Long): Long = milisegundos * 1_000_000L
