package com.neiderlizcano.gestorpedidos.nativo.sensores

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Implementación real sobre [SensorManager]. Una instancia por pantalla/aplicación; no guarda estado de UI. */
class AndroidSensorHardware(private val manager: SensorManager?) : SensorHardware {
    // Cada EscuchaSensor tiene como máximo un SensorEventListener del sistema asociado.
    private val registros = HashMap<EscuchaSensor, SensorEventListener>()

    private fun sensor(tipo: TipoSensor): Sensor? = manager?.getDefaultSensor(
        when (tipo) {
            TipoSensor.ACELEROMETRO -> Sensor.TYPE_ACCELEROMETER
            TipoSensor.PROXIMIDAD -> Sensor.TYPE_PROXIMITY
        }
    )

    override fun informacion(tipo: TipoSensor): InfoSensor? =
        sensor(tipo)?.let { InfoSensor(it.name, it.maximumRange) }

    override fun registrar(tipo: TipoSensor, escucha: EscuchaSensor): Boolean {
        val gestor = manager ?: return false
        val sensor = sensor(tipo) ?: return false
        val receptor = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                escucha.alRecibir(event.values.copyOf(), event.accuracy, event.timestamp)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registrado = try {
            gestor.registerListener(receptor, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (_: RuntimeException) {
            false
        }
        if (registrado) synchronized(registros) { registros[escucha] = receptor }
        return registrado
    }

    override fun liberar(escucha: EscuchaSensor) {
        val receptor = synchronized(registros) { registros.remove(escucha) } ?: return
        manager?.unregisterListener(receptor)
    }

    /** Para pruebas instrumentadas: cuántas escuchas siguen activas. */
    internal fun suscripcionesActivas(): Int = synchronized(registros) { registros.size }

    companion object {
        fun desde(context: Context): AndroidSensorHardware =
            AndroidSensorHardware(context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
    }
}
