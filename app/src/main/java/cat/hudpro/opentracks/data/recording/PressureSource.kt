package cat.hudpro.opentracks.data.recording

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import cat.hudpro.opentracks.data.debug.DebugLog

/** Barometric pressure samples for steadier elevation gain/loss (like OpenTracks' GainManager). */
class PressureSource(private val context: Context) {

    private var manager: SensorManager? = null
    private var listener: SensorEventListener? = null

    /** Starts sampling; returns false if the device has no barometer. */
    fun start(onPressure: (Float) -> Unit): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return false
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let(onPressure)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        manager = sm
        listener = l
        DebugLog.i("Record", "baròmetre actiu")
        return true
    }

    fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
        manager = null
    }
}
