package dev.voxpilot.assistant.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class TouchGuardActivity : AppCompatActivity() {

    private var player: MediaPlayer? = null
    private var isAlarmTriggered = false
    private lateinit var statusMessage: TextView

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAlarm()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch_guard)

        statusMessage = findViewById(R.id.statusMessage)
        val disarmBtn = findViewById<Button>(R.id.disarmBtn)

        disarmBtn.setOnClickListener {
            stopAlarm()
            finish()
        }

        val filter = IntentFilter("VOXPILOT_STOP_TOUCH_GUARD")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN && !isAlarmTriggered) {
            triggerSiren()
        }
        return super.onTouchEvent(event)
    }

    private fun triggerSiren() {
        isAlarmTriggered = true
        statusMessage.text = "🚨 ALARM: Unauthorized touch detected! Someone touched your phone!"
        statusMessage.setTextColor(0xFFFF1744.toInt())

        // Set volume to max
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
                setDataSource(this@TouchGuardActivity, uri)
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
