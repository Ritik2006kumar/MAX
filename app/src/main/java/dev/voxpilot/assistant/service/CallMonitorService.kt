package dev.voxpilot.assistant.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.ui.main.CallAssistantActivity
import java.util.*

class CallMonitorService : Service(), TextToSpeech.OnInitListener, SensorEventListener {

    private var telephonyManager: TelephonyManager? = null
    private var phoneListener: PhoneStateListener? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isRinging = false
    private var alreadyMuted = false

    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var announced = false

    companion object {
        const val ACTION_CALL_ACTIVE = "dev.voxpilot.assistant.CALL_ACTIVE"
        const val ACTION_CALL_ENDED = "dev.voxpilot.assistant.CALL_ENDED"
        const val ACTION_CALL_RINGING = "dev.voxpilot.assistant.CALL_RINGING"
        private const val CHANNEL_ID = "voxpilot_call_channel"
        private const val TAG = "VoxPilot_CALL"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForegroundService()

        tts = TextToSpeech(this, this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setupPhoneListener()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            ttsReady = true
        }
    }

    private fun setupPhoneListener() {
        telephonyManager =
            getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        phoneListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, number: String?) {
                super.onCallStateChanged(state, number)

                when (state) {

                    TelephonyManager.CALL_STATE_RINGING -> {
                        isRinging = true
                        alreadyMuted = false
                        accelerometer?.let {
                            sensorManager?.registerListener(this@CallMonitorService, it, SensorManager.SENSOR_DELAY_NORMAL)
                        }
                        if (!announced) {
                            announced = true
                            handleIncomingCall(number)
                        }
                    }

                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        isRinging = false
                        sensorManager?.unregisterListener(this@CallMonitorService)
                        sendBroadcast(Intent(ACTION_CALL_ACTIVE))
                    }

                    TelephonyManager.CALL_STATE_IDLE -> {
                        isRinging = false
                        sensorManager?.unregisterListener(this@CallMonitorService)
                        announced = false
                        sendBroadcast(Intent(ACTION_CALL_ENDED))
                    }
                }

                lastState = state
            }
        }

        telephonyManager?.listen(
            phoneListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && isRinging && !alreadyMuted) {
            val z = event.values[2]
            // If phone is flipped face down (z < -8.0)
            if (z < -8.0f) {
                alreadyMuted = true
                try {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    if (ttsReady && tts != null) {
                        tts?.speak("Call silent kar diya", TextToSpeech.QUEUE_FLUSH, null, "FLIP_MUTE")
                    }
                    Log.d(TAG, "Phone flipped face down -> Call muted ✅")
                } catch (e: Exception) {
                    Log.e(TAG, "Flip mute failed: ${e.message}")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleIncomingCall(number: String?) {
        val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
        val driving = prefs.getBoolean(dev.voxpilot.assistant.utils.Constants.KEY_DRIVING_MODE, false)
        val sleeping = prefs.getBoolean(dev.voxpilot.assistant.utils.Constants.KEY_SLEEP_MODE, false)

        if (driving || sleeping) {
            Log.d(TAG, "Auto-replying due to " + (if (driving) "driving" else "sleeping") + " mode")
            if (!number.isNullOrEmpty()) {
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }
                    val msg = if (driving) "I am currently driving. I will call you back later." else "I am currently sleeping. I will call you back later."
                    smsManager?.sendTextMessage(number, null, msg, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-reply SMS failed: ${e.message}")
                }
            }
        }

        val callerName = resolveCallerName(number)

        Log.d(TAG, "Incoming: $callerName")

        val intent = Intent(this, CallAssistantActivity::class.java).apply {
            putExtra("CALLER_NAME", callerName)
            putExtra("PHONE_NUMBER", number ?: "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(intent)
    }

    private fun resolveCallerName(number: String?): String {
        if (number.isNullOrEmpty()) return "Unknown Caller"

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Unknown Caller"
        }

        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )

            contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0) ?: "Unknown Caller"
                } else {
                    "Unknown Caller"
                }
            } ?: "Unknown Caller"

        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "")
            "Unknown Caller"
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Max Running")
            .setContentText("Monitoring calls")
            .setSmallIcon(R.mipmap.img)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Max Call Monitor",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }
}
