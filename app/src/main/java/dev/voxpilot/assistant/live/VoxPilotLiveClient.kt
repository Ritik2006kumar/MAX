package dev.voxpilot.assistant.live

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live API client (BidiGenerateContent WebSocket): streams mic audio up, spoken audio and
 * function calls down. The model decides which device tool to call (see [LiveTools]); the result
 * goes back through [sendToolResponse] so it can speak the real outcome.
 */
class VoxPilotLiveClient(
    private val apiKey: String,
    private val modelId: String,
    private val listener: Listener,
) {
    interface Listener {
        fun onSetupComplete()
        fun onAudioChunk(pcm16: ByteArray)
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onTurnComplete()
        fun onInterrupted()
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onError(message: String)
        fun onClosed()
    }

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    @Volatile private var setupDone = false
    private val seenToolCalls = LinkedHashSet<String>()

    val isReady: Boolean get() = setupDone

    fun connect(systemInstruction: String, voiceName: String) {
        val url = if (apiKey.isNotBlank()) {
            if (WS_URL.contains("?")) "$WS_URL&key=$apiKey" else "$WS_URL?key=$apiKey"
        } else WS_URL

        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .build()

        webSocket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildSetup(systemInstruction, voiceName))
            }

            // No raw-message logging here: every audio chunk is base64 and Logcat I/O on this
            // thread adds real latency to replies.
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val body = try { response?.body?.string() } catch (_: Exception) { null }
                Log.e(TAG, "onFailure: HTTP $code body=$body", t)
                val msg = when {
                    code == 400 || code == 403 -> "API Key invalid ya unauthorized hai ($code). AI Studio se nayi key check karo."
                    code == 404 -> "Model not found ($code)."
                    code != null -> "Connection error HTTP $code"
                    else -> t.message ?: "Connection failed"
                }
                listener.onError(msg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "onClosed: code=$code reason=$reason")
                if (code != 1000) listener.onError("Connection closed ($code): $reason") else listener.onClosed()
            }
        })
    }

    fun sendAudioChunk(pcm16: ByteArray) {
        if (!setupDone) return // a socket that has not finished setup rejects audio with 1007
        val msg = JSONObject().put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("mimeType", "audio/pcm;rate=16000")
            .put("data", Base64.encodeToString(pcm16, Base64.NO_WRAP))))
        webSocket?.send(msg.toString())
    }

    /** Types a user turn into the live conversation (used by the on-screen mic/text button). */
    fun sendText(text: String) {
        if (!setupDone) return
        val msg = JSONObject().put("realtimeInput", JSONObject().put("text", text))
        webSocket?.send(msg.toString())
    }

    /** Pushes one screenshot (JPEG) into the conversation so the model sees the phone's screen. */
    fun sendImageFrame(jpeg: ByteArray): Boolean {
        if (!setupDone) return false
        val msg = JSONObject().put("realtimeInput", JSONObject().put("video", JSONObject()
            .put("mimeType", "image/jpeg")
            .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
        return webSocket?.send(msg.toString()) == true
    }

    fun sendToolResponse(id: String, name: String, result: JSONObject) {
        val msg = JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(
            JSONObject().put("id", id).put("name", name).put("response", result),
        )))
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        setupDone = false
        webSocket?.close(1000, "client closing")
        webSocket = null
    }

    private fun buildSetup(systemInstruction: String, voiceName: String): String = JSONObject().put("setup", JSONObject().apply {
        put("model", "models/$modelId")
        put("generationConfig", JSONObject().apply {
            put("responseModalities", JSONArray().put("AUDIO"))
            put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))))
        })
        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
        put("inputAudioTranscription", JSONObject())
        put("outputAudioTranscription", JSONObject())
        put("tools", JSONArray().put(JSONObject().put("functionDeclarations", LiveTools.declarations())))
    }).toString()

    private fun handle(text: String) {
        val json = try { JSONObject(text) } catch (e: Exception) {
            Log.e(TAG, "Bad JSON from server", e)
            return
        }

        if (json.has("setupComplete")) {
            setupDone = true
            listener.onSetupComplete()
            return
        }

        json.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                val id = call.optString("id")
                val name = call.optString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                // A redelivered call must not run twice: tapping/messaging is not idempotent.
                val first = id.isBlank() || synchronized(seenToolCalls) {
                    seenToolCalls.add(id).also { if (seenToolCalls.size > 500) seenToolCalls.remove(seenToolCalls.first()) }
                }
                if (first) listener.onToolCall(id, name, args)
                else sendToolResponse(id, name, JSONObject().put("result", "duplicate_tool_call_ignored"))
            }
            return
        }

        if (json.has("sessionResumptionUpdate") || json.has("goAway") || json.has("usageMetadata")) return

        val content = json.optJSONObject("serverContent") ?: return

        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                if (inline.optString("mimeType").startsWith("audio/pcm")) {
                    listener.onAudioChunk(Base64.decode(inline.optString("data"), Base64.DEFAULT))
                }
            }
        }
        content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let(listener::onInputTranscript)
        content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let(listener::onOutputTranscript)
        if (content.optBoolean("turnComplete", false)) listener.onTurnComplete()
        if (content.optBoolean("interrupted", false)) listener.onInterrupted()
    }

    companion object {
        private const val TAG = "VoxPilotLiveClient"
        private const val WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }
}
