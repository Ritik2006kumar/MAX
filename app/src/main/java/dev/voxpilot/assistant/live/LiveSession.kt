package dev.voxpilot.assistant.live

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dev.voxpilot.assistant.utils.Constants
import dev.voxpilot.assistant.utils.prefs
import dev.voxpilot.assistant.voice.VoiceStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Owns the live Gemini conversation (WebSocket + mic + speaker) independent of any Activity, so it
 * keeps running while VoxPilot is in the background. Gemini calls tools; [DeviceActions] performs them.
 *
 * Reliability: auto-reconnect with backoff, instant reconnect when the network returns, a handshake
 * watchdog, stale-callback protection (epoch), and fallback to the next voice model if one keeps
 * failing before its handshake completes.
 */
object LiveSession {
    private const val TAG = "LiveSession"

    /** Newest first; checked end-to-end against the Live API. The next one is used if one keeps failing. */
    val MODELS = listOf(
        "gemini-3.8-live",
        "gemini-3.1-flash-live-preview",
        "gemini-2.5-flash-native-audio-preview-12-2025",
        "gemini-2.0-flash-exp",
    )

    private const val SETUP_TIMEOUT_MS = 9000L
    private const val PLAYBACK_SILENCE_GAP_MS = 300L
    private val RECONNECT_DELAYS_MS = longArrayOf(500L, 1000L, 2000L, 4000L, 6000L, 10000L)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var appContext: Context? = null
    private var client: VoxPilotLiveClient? = null
    private var audio: LiveAudioIO? = null
    private var reconnectJob: Job? = null
    private var setupWatchdog: Job? = null
    private var silenceWatchdog: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile private var active = false
    @Volatile var muted = false
        private set
    @Volatile private var epoch = 0
    @Volatile private var micStarted = false
    @Volatile private var playbackActive = false
    @Volatile private var networkLost = false
    @Volatile private var failedHandshakes = 0
    @Volatile private var modelIndex = 0
    private var attempt = 0

    val isActive: Boolean get() = active

    fun start(context: Context) {
        if (active) return
        val app = context.applicationContext
        appContext = app
        val key = app.prefs().getString(Constants.KEY_API_KEY, "").orEmpty()
        if (key.isBlank()) {
            VoiceStateManager.setError("Settings mein Gemini API key daalo")
            return
        }
        active = true
        muted = false
        attempt = 0
        failedHandshakes = 0
        modelIndex = MODELS.indexOf(app.prefs().getString("voice_model", MODELS[0])).coerceAtLeast(0)
        Apps.prewarm(app)
        VoiceStateManager.setThinking()
        VoiceStateManager.statusMessage.postValue("CONNECTING...")
        scope.launch { mutex.withLock { connect(app, resuming = false) } }
    }

    fun stop() {
        active = false
        epoch++
        reconnectJob?.cancel(); reconnectJob = null
        setupWatchdog?.cancel(); setupWatchdog = null
        silenceWatchdog?.cancel(); silenceWatchdog = null
        unwatchNetwork()
        client?.disconnect(); client = null
        audio?.release(); audio = null
        micStarted = false
        playbackActive = false
        EchoGuard.reset()
        VoiceStateManager.setIdle()
    }

    /** Forces a fresh connection (long-press on the mic button). */
    fun reconnect() {
        val app = appContext ?: return
        if (!active) { start(app); return }
        attempt = 0
        reconnectJob?.cancel()
        reconnectJob = scope.launch { mutex.withLock { if (active) connect(app, resuming = true) } }
    }

    /** Mic on/off without dropping the connection (the orb button toggles it). */
    fun setMuted(value: Boolean) {
        muted = value
        if (value) VoiceStateManager.setIdle() else if (active) VoiceStateManager.setListening()
    }

    fun sendFrame(jpeg: ByteArray): Boolean = if (active) client?.sendImageFrame(jpeg) == true else false

    fun sendText(text: String) {
        client?.sendText(text)
    }

    // ---------------------------------------------------------------------------------------

    private fun connect(context: Context, resuming: Boolean) {
        val myEpoch = ++epoch
        client?.disconnect()
        if (audio == null) {
            audio = LiveAudioIO(::onMicChunk, ::onMicAmplitude)
        }
        val p = context.prefs()
        val stealth = p.getBoolean(Constants.KEY_STEALTH_MODE, false)
        val micMuted = p.getBoolean(Constants.KEY_MIC_MUTED, false)
        setMuted(micMuted)

        if (stealth) {
            audio?.setVolume(0f)
        } else {
            audio?.setVolume(p.getFloat("voice_volume", 1f))
        }
        micStarted = false

        val apiKey = p.getString(Constants.KEY_API_KEY, "").orEmpty().trim()
        if (apiKey.isBlank()) {
            Log.e(TAG, "Gemini API Key is blank!")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Gemini API Key missing! Settings me API Key dalo.", Toast.LENGTH_LONG).show()
            }
            return
        }

        val instruction = LiveTools.systemInstruction(
            userName = p.getString(Constants.KEY_USER_NAME, "Sir").orEmpty().ifBlank { "Sir" },
            personality = p.getString("personality_mode", "gf").orEmpty(),
            assistantName = p.getString(Constants.KEY_ASSISTANT_NAME, "Max").orEmpty().ifBlank { "Max" },
            stealthMode = stealth,
            context = context
        ) + if (resuming) "\n\nYe ek chalti hui baatcheet hai - dobara greet mat karo, seedha kaam par aao."
        else "\n\nPehli baar milne par sirf ek chhota sa greeting do, bas."

        val model = MODELS[modelIndex % MODELS.size]
        Log.d(TAG, "connecting model=$model resuming=$resuming")
        val live = VoxPilotLiveClient(p.getString(Constants.KEY_API_KEY, "").orEmpty(), model, listener(context, myEpoch))
        client = live
        live.connect(instruction, p.getString("voice_name", Constants.GEMINI_VOICE).orEmpty().ifBlank { Constants.GEMINI_VOICE })
        watchNetwork(context)

        setupWatchdog?.cancel()
        setupWatchdog = scope.launch {
            delay(SETUP_TIMEOUT_MS)
            if (myEpoch == epoch && active && client?.isReady != true) {
                Log.w(TAG, "handshake timed out")
                noteHandshakeFailure()
                scheduleReconnect(context)
            }
        }
    }

    /** After two handshakes in a row fail, try the next model - one may be retired or having an outage. */
    private fun noteHandshakeFailure() {
        failedHandshakes++
        if (failedHandshakes >= 2) {
            failedHandshakes = 0
            modelIndex = (modelIndex + 1) % MODELS.size
            Log.w(TAG, "switching to model ${MODELS[modelIndex]}")
        }
    }

    private fun scheduleReconnect(context: Context) {
        if (!active || reconnectJob?.isActive == true) return
        val wait = RECONNECT_DELAYS_MS[attempt.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
        attempt++
        VoiceStateManager.statusMessage.postValue("RECONNECTING...")
        reconnectJob = scope.launch {
            delay(wait)
            mutex.withLock { if (active) connect(context, resuming = true) }
        }
    }

    private fun onMicChunk(chunk: ByteArray) {
        if (!muted && !EchoGuard.isMicMuted()) client?.sendAudioChunk(chunk)
    }

    private fun onMicAmplitude(level: Float) {
        if (muted || EchoGuard.isMicMuted() || VoiceStateManager.isAiSpeaking()) return
        VoiceStateManager.updateAmplitude(level)
    }

    // Transcripts arrive in small fragments; the chat gets one bubble per finished utterance.
    private val userBuf = StringBuilder()
    private val replyBuf = StringBuilder()

    private fun flushUser(context: Context) {
        val text = synchronized(userBuf) { userBuf.toString().trim().also { userBuf.setLength(0) } }
        if (text.isNotEmpty()) {
            dev.voxpilot.assistant.ai.ConversationMemory.addUser(context, text)
            context.sendBroadcast(android.content.Intent("VoxPilot_USER_SAID").setPackage(context.packageName).putExtra("text", text))
        }
    }

    private fun flushReply(context: Context) {
        val text = synchronized(replyBuf) { replyBuf.toString().trim().also { replyBuf.setLength(0) } }
        if (text.isNotEmpty()) {
            dev.voxpilot.assistant.ai.ConversationMemory.addAssistant(context, text)
            context.sendBroadcast(android.content.Intent("VoxPilot_RESPONSE").setPackage(context.packageName).putExtra("text", text))
        }
    }

    private fun turnEnded() {
        if (playbackActive) {
            playbackActive = false
            EchoGuard.notifyPlaybackStopped()
        }
        VoiceStateManager.setListening()
        VoiceStateManager.updateAmplitude(0f)
    }

    private fun listener(context: Context, myEpoch: Int) = object : VoxPilotLiveClient.Listener {
        private fun current() = myEpoch == epoch

        override fun onSetupComplete() {
            if (!current()) return
            Log.d(TAG, "connected")
            setupWatchdog?.cancel()
            failedHandshakes = 0
            attempt = 0
            VoiceStateManager.setListening()
            if (!micStarted) {
                micStarted = true
                audio?.startMic()
            }
        }

        override fun onAudioChunk(pcm16: ByteArray) {
            if (!current()) return
            if (!playbackActive) {
                playbackActive = true
                EchoGuard.notifyPlaybackStarted()
                VoiceStateManager.setSpeaking()
            }
            val level = audio?.playChunk(pcm16) ?: 0f
            VoiceStateManager.updateAmplitude(level)
            // The server's turnComplete can trail by seconds; this unmutes the mic as soon as audio really stops.
            silenceWatchdog?.cancel()
            silenceWatchdog = scope.launch {
                delay(PLAYBACK_SILENCE_GAP_MS)
                turnEnded()
            }
        }

        override fun onInputTranscript(text: String) {
            if (!current()) return
            synchronized(userBuf) { userBuf.append(text) }
        }

        override fun onOutputTranscript(text: String) {
            if (!current()) return
            flushUser(context)
            synchronized(replyBuf) { replyBuf.append(text) }
        }

        override fun onTurnComplete() {
            if (!current()) return
            silenceWatchdog?.cancel()
            flushUser(context)
            flushReply(context)
            turnEnded()
        }

        override fun onInterrupted() {
            if (!current()) return
            silenceWatchdog?.cancel()
            audio?.flushPlayback()
            flushReply(context)
            turnEnded()
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            if (!current()) return
            VoiceStateManager.setThinking()
            scope.launch {
                val result = DeviceActions.execute(name, args, context)
                if (current()) client?.sendToolResponse(id, name, result)
            }
        }

        override fun onError(message: String) {
            if (!current()) return
            Log.e(TAG, "error: $message")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "VoxPilot: $message", Toast.LENGTH_SHORT).show()
            }
            if (client?.isReady != true) noteHandshakeFailure()
            scheduleReconnect(context)
        }

        override fun onClosed() {
            if (!current()) return
            Log.w(TAG, "closed")
            scheduleReconnect(context)
        }
    }

    private fun watchNetwork(context: Context) {
        if (netCallback != null) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) { networkLost = true }
            override fun onAvailable(network: Network) {
                if (!networkLost || !active) return
                networkLost = false
                Log.w(TAG, "network is back - reconnecting now")
                attempt = 0
                reconnectJob?.cancel()
                reconnectJob = scope.launch { mutex.withLock { if (active) connect(context, resuming = true) } }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { netCallback = callback }
    }

    private fun unwatchNetwork() {
        val callback = netCallback ?: return
        netCallback = null
        runCatching { appContext?.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }
}
