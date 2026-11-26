package dev.voxpilot.assistant.automation

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import dev.voxpilot.assistant.utils.prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

object FindMyPhoneManager {

    private const val TAG = "FindMyPhoneManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var findJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    var isRunning: Boolean = false
        private set

    fun start(context: Context) {
        stop(context)
        isRunning = true

        val prefs = context.prefs()
        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val userName = prefs.getString("user_name", "Sir").orEmpty().ifBlank { "Sir" }

        val speakText = when (personality) {
            "gf" -> "$userName! Main yahan hoon! Yahan dekho!"
            "professional" -> "Attention $userName. Phone location signal. I am right here."
            "caring" -> "$userName, main yahan hoon! Aapka phone mil gaya."
            "emotional" -> "$userName, main yahan hoon! Mujhe akela mat chhodo!"
            "nautanki" -> "Arey $userName! Dhishoom! Main yahan hoon re baba!"
            else -> "I am right here! Main yahan hoon!"
        }

        // Max volume
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (_: Exception) {}

        // Setup TTS
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                ttsReady = true
            }
        }

        findJob = scope.launch {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = try { cm?.cameraIdList?.firstOrNull { id -> cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } } catch (_: Exception) { null }

            while (isActive && isRunning) {
                // Torch ON
                if (cm != null && cameraId != null) {
                    try { cm.setTorchMode(cameraId, true) } catch (_: Exception) {}
                }

                // Speak
                if (ttsReady && tts != null) {
                    tts?.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "FIND_MY_PHONE")
                }

                // Vibrate
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                val pattern = longArrayOf(0, 400, 200, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }

                delay(800)

                // Torch OFF
                if (cm != null && cameraId != null) {
                    try { cm.setTorchMode(cameraId, false) } catch (_: Exception) {}
                }

                delay(1200)
            }
        }
    }

    fun stop(context: Context) {
        isRunning = false
        findJob?.cancel()
        findJob = null

        // Turn off torch
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cm?.cameraIdList?.firstOrNull { id -> cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (cm != null && cameraId != null) {
                cm.setTorchMode(cameraId, false)
            }
        } catch (_: Exception) {}

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ttsReady = false

        (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }
}
