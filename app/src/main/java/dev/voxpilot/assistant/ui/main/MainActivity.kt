package dev.voxpilot.assistant.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.live.LiveSession
import dev.voxpilot.assistant.security.BiometricManager
import dev.voxpilot.assistant.service.AccessibilityHelperService
import dev.voxpilot.assistant.services.ForegroundVoiceService
import dev.voxpilot.assistant.ui.settings.SettingsActivity
import dev.voxpilot.assistant.utils.Constants
import dev.voxpilot.assistant.utils.Logger
import dev.voxpilot.assistant.utils.PermissionUtils
import dev.voxpilot.assistant.utils.prefs
import dev.voxpilot.assistant.utils.toast
import dev.voxpilot.assistant.voice.VoiceStateManager

class MainActivity : AppCompatActivity() {

    private val TAG = "MAIN"

    private lateinit var orbView: MicOrbView
    private lateinit var ribbonView: RibbonWaveView
    private lateinit var statusText: TextView
    private lateinit var askInput: EditText
    private lateinit var chatPanel: View
    private lateinit var chatRecycler: RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    private val chatReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val text = intent?.getStringExtra("text") ?: return
            if (intent.action == "VoxPilot_USER_SAID") addUserMessage(text) else addBotMessage(text)
        }
    }

    private val chatFilter = IntentFilter().apply {
        addAction("VoxPilot_RESPONSE")
        addAction("VoxPilot_USER_SAID")
    }

    companion object {
        const val PERM_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#080508")
        setContentView(R.layout.activity_main)

        initViews()
        dev.voxpilot.assistant.automation.ShakeDetectorManager.init(this)
        dev.voxpilot.assistant.security.NightAutoLockManager.start(this)
        dev.voxpilot.assistant.security.ContinuousSecurityScanner.start(this)

        BiometricManager.authenticate(
            this,
            onSuccess = { setupAssistant() },
            onError = { setupAssistant() },
            onFallback = { setupAssistant() },
        )
    }

    private fun updateAssistantName() {
        val assistantName = prefs().getString(Constants.KEY_ASSISTANT_NAME, "MAX")?.uppercase() ?: "MAX"
        val parts = assistantName.trim().split("\\s+".toRegex(), limit = 2)
        val firstPart = findViewById<TextView>(R.id.titleFirstPart)
        val secondPart = findViewById<TextView>(R.id.titleSecondPart)
        if (parts.size >= 2) {
            firstPart.text = parts[0]
            firstPart.visibility = View.VISIBLE
            secondPart.text = parts[1]
            secondPart.visibility = View.VISIBLE
        } else {
            firstPart.text = ""
            firstPart.visibility = View.GONE
            secondPart.text = parts[0]
            secondPart.visibility = View.VISIBLE
        }
    }

    private fun setupAssistant() {
        PermissionUtils.requestMissing(this, PERM_CODE)
        observeVoiceState()
        startVoiceService()
        checkAccessibility()
    }

    private fun initViews() {
        orbView = findViewById(R.id.orbView)
        ribbonView = findViewById(R.id.ribbonView)
        statusText = findViewById(R.id.statusText)
        askInput = findViewById(R.id.askInput)
        chatPanel = findViewById(R.id.chatPanel)
        chatRecycler = findViewById(R.id.chatRecycler)

        chatAdapter = ChatAdapter()
        chatRecycler.layoutManager = LinearLayoutManager(this).also { it.stackFromEnd = true }
        chatRecycler.adapter = chatAdapter

        // Tap: start VoxPilot if she isn't running, otherwise mute / unmute the mic.
        orbView.setOnClickListener {
            if (!ForegroundVoiceService.isRunning || !LiveSession.isActive) {
                startVoiceService()
            } else {
                LiveSession.setMuted(!LiveSession.muted)
            }
        }
        orbView.setOnLongClickListener {
            LiveSession.reconnect()
            toast("Reconnecting...")
            true
        }

        findViewById<ImageButton>(R.id.menuBtn).setOnClickListener {
            chatPanel.visibility = if (chatPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<ImageButton>(R.id.hudDashboardBtn).setOnClickListener {
            startActivity(Intent(this, dev.voxpilot.assistant.security.QuantumDashboardActivity::class.java))
        }
        findViewById<View>(R.id.titleBox).setOnClickListener {
            startActivity(Intent(this, dev.voxpilot.assistant.security.QuantumDashboardActivity::class.java))
        }
        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<ImageButton>(R.id.sendBtn).setOnClickListener { sendTyped() }
        askInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                sendTyped()
                true
            } else false
        }
    }

    private fun sendTyped() {
        val text = askInput.text.toString().trim()
        if (text.isEmpty()) return
        if (!ForegroundVoiceService.isRunning || !LiveSession.isActive) {
            startVoiceService()
            toast("VoxPilot connect ho rahi hai, thodi der baad dobara bhejo")
            return
        }
        LiveSession.sendText(text)
        addUserMessage(text)
        askInput.text.clear()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(askInput.windowToken, 0)
    }

    private fun observeVoiceState() {
        VoiceStateManager.state.observe(this) { state ->
            val orbState = when (state) {
                Constants.STATE_LISTENING -> MicOrbView.State.LISTENING
                Constants.STATE_THINKING -> MicOrbView.State.THINKING
                Constants.STATE_SPEAKING -> MicOrbView.State.SPEAKING
                else -> MicOrbView.State.IDLE
            }
            orbView.setState(orbState)
            ribbonView.setState(orbState)
            statusText.text = when (orbState) {
                MicOrbView.State.LISTENING -> "Listening..."
                MicOrbView.State.THINKING -> "Thinking..."
                MicOrbView.State.SPEAKING -> "Speaking..."
                MicOrbView.State.IDLE -> if (LiveSession.isActive && LiveSession.muted) "Mic off - Tap to Talk" else "Tap to Talk"
            }
        }
        VoiceStateManager.amplitude.observe(this) { amp ->
            orbView.setLevel(amp)
            ribbonView.setLevel(amp)
        }
        VoiceStateManager.statusMessage.observe(this) { msg ->
            // Connection problems are worth showing; routine states are mapped above.
            if (msg.startsWith("RECONNECTING") || msg.startsWith("CONNECTING") || msg.contains("API key")) {
                statusText.text = msg.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }

    private fun startVoiceService() {
        val apiKey = prefs().getString(Constants.KEY_API_KEY, "") ?: ""
        if (apiKey.isEmpty()) {
            addBotMessage("API key chahiye. Settings mein Gemini API key daalo.")
            toast("Settings mein Gemini API key daalo")
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, ForegroundVoiceService::class.java))
        Logger.d(TAG, "Voice service started")
    }

    private fun checkAccessibility() {
        if (!AccessibilityHelperService.isEnabled(this)) {
            addBotMessage("Phone control ke liye Settings > Accessibility mein VoxPilot ON karo.")
        }
    }

    fun addUserMessage(text: String) = runOnUiThread {
        chatAdapter.addMessage(ChatMessage(text, true))
        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
    }

    fun addBotMessage(text: String) = runOnUiThread {
        chatAdapter.addMessage(ChatMessage(text, false))
        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
    }

    override fun onResume() {
        super.onResume()
        updateAssistantName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(chatReceiver, chatFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(chatReceiver, chatFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(chatReceiver) } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // The mic permission is the one the voice session cannot start without.
        if (requestCode == PERM_CODE && PermissionUtils.hasMicPermission(this)) startVoiceService()
    }
}
