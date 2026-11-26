package dev.voxpilot.assistant.automation

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import dev.voxpilot.assistant.services.ForegroundVoiceService
import kotlin.math.sqrt

object ShakeDetectorManager : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var appContext: Context? = null
    private var lastShakeTime = 0L
    private const val SHAKE_THRESHOLD = 12.0f
    private const val SHAKE_COOLDOWN_MS = 3000L

    fun init(context: Context) {
        if (sensorManager != null) return
        appContext = context.applicationContext
        sensorManager = appContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

            if (gForce > SHAKE_THRESHOLD) {
                val now = System.currentTimeMillis()
                if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {
                    lastShakeTime = now
                    appContext?.let { ctx ->
                        try {
                            val intent = Intent(ctx, ForegroundVoiceService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ctx.startForegroundService(intent)
                            } else {
                                ctx.startService(intent)
                            }
                            Log.d("ShakeDetector", "Phone shaken -> Activating Max! ⚡")
                        } catch (e: Exception) {
                            Log.e("ShakeDetector", "Failed to activate on shake: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
