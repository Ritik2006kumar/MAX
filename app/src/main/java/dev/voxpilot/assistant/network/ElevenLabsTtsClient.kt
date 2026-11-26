package dev.voxpilot.assistant.network

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ElevenLabsTtsClient {
    private const val TAG = "ElevenLabsTTS"
    private var mediaPlayer: MediaPlayer? = null

    suspend fun speak(context: Context, text: String, apiKey: String, voiceId: String = "21m00Tcm4TlvDq8ikWAM"): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || text.isBlank()) return@withContext false

        val targetVoice = voiceId.ifBlank { "21m00Tcm4TlvDq8ikWAM" }
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$targetVoice"

        val json = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            })
        }

        val client = OkHttpClient()
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("xi-api-key", apiKey)
            .addHeader("Accept", "audio/mpeg")
            .post(requestBody)
            .build()

        return@withContext try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    Log.e(TAG, "ElevenLabs API failed with code: $code")
                    withContext(Dispatchers.Main) {
                        val errorMsg = when (code) {
                            401 -> "ElevenLabs API Key invalid hai! Settings mein sahi key daalo."
                            429 -> "ElevenLabs free quota khatam ho gaya hai!"
                            else -> "ElevenLabs API error HTTP $code"
                        }
                        android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@withContext false
                }

                val bytes = response.body?.bytes() ?: return@withContext false
                val audioFile = File(context.cacheDir, "elevenlabs_${System.currentTimeMillis()}.mp3")
                FileOutputStream(audioFile).use { it.write(bytes) }

                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(audioFile.absolutePath)
                            prepare()
                            start()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer error: ${e.message}")
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs request error: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }
}
