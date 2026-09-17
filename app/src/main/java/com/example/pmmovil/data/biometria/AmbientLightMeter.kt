package com.example.pmmovil.data.biometria

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Lector del sensor de luz ambiental, en lux.
 *
 * Es la variable central del laboratorio: la pregunta que hay que contestar es
 * a partir de que nivel de oscuridad el reconocimiento facial deja de servir, y
 * sin una medida objetiva de la luz solo quedarian impresiones ("estaba
 * oscuro"). Referencias utiles al leer los resultados:
 *
 *   ~1 lux      noche con luna
 *   ~10 lux     pasillo de bodega sin lampara encima
 *   ~50 lux     nave mal iluminada de noche
 *   ~300 lux    oficina normal
 *   ~1000 lux   dia nublado bajo techo
 *   ~10000 lux  exterior a la sombra
 *
 * No todos los equipos traen el sensor. Si falta, [lux] se queda en null y el
 * laboratorio sigue funcionando apoyandose en la luma del recorte facial, que
 * se calcula siempre.
 */
class AmbientLightMeter(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    @Volatile
    var lux: Float? = null
        private set

    val disponible: Boolean get() = sensor != null

    fun iniciar() {
        val manager = sensorManager ?: return
        val luz = sensor ?: return
        manager.registerListener(this, luz, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun detener() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            lux = event.values.firstOrNull()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
