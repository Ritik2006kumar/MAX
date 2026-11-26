package dev.voxpilot.assistant.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import kotlin.math.sqrt

class MotionGuardActivity : AppCompatActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var player: MediaPlayer? = null
    private var isAlarmTriggered = false
    private lateinit var statusMessage: TextView

    private var lastAccel = 0f
    private var accelCurrent = SensorManager.GRAVITY_EARTH
    private var accelLast = SensorManager.GRAVITY_EARTH

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAlarm()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motion_guard)

        statusMessage = findViewById(R.id.statusMessage)
        val disarmBtn = findViewById<Button>(R.id.disarmBtn)

        disarmBtn.setOnClickListener {
            stopAlarm()
            finish()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val filter = IntentFilter("VOXPILOT_STOP_MOTION_GUARD")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && !isAlarmTriggered) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            accelLast = accelCurrent
            accelCurrent = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = accelCurrent - accelLast
            lastAccel = lastAccel * 0.9f + delta

            if (lastAccel > 2.2f) {
                triggerSiren()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerSiren() {
        isAlarmTriggered = true
        statusMessage.text = "🚨 ALARM: Motion detected! Phone was moved or picked up!"
        statusMessage.setTextColor(0xFFFF1744.toInt())

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@MotionGuardActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    override fun onBackPressed() {
        stopAlarm()
        super.onBackPressed()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopReceiver)
        } catch (_: Exception) {}
        stopAlarm()
        super.onDestroy()
    }
}
