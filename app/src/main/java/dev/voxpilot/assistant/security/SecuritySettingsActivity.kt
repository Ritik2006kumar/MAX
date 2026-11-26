package dev.voxpilot.assistant.security

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Bundle as AndroidBundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.live.LiveSession
import java.util.Locale

/**
 * Privacy & Security: App Lock, unlock methods (PIN, pattern, voice passphrase, fingerprint),
 * the permissions App Lock needs, private mode, and a live protection score.
 */
class SecuritySettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var appLockSwitch: CompoundButton
    private lateinit var appLockStatusText: TextView
    private lateinit var lockedAppsText: TextView
    private lateinit var setPinBtn: View
    private lateinit var pinStatusText: TextView
    private lateinit var setPatternBtn: View
    private lateinit var patternStatusText: TextView
    private lateinit var setVoiceBtn: View
    private lateinit var voiceStatusText: TextView
    private lateinit var selectAppsBtn: View
    private lateinit var usageStatsBtn: View
    private lateinit var usageStatus: TextView
    private lateinit var overlayPermissionBtn: View
    private lateinit var overlayStatus: TextView
    private lateinit var fingerprintSwitch: CompoundButton
    private lateinit var rowFingerprint: View
    private lateinit var deviceLockSwitch: CompoundButton
    private lateinit var privateModeSwitch: CompoundButton
    private lateinit var privateModeStatusText: TextView
    private lateinit var permissionAnalyzerBtn: View
    private lateinit var networkSecurityBtn: View
    private lateinit var phishingDetectorBtn: View
    private lateinit var qrScannerBtn: View
    private lateinit var quantumDashboardBtn: View
    private lateinit var scoreTitle: TextView
    private lateinit var scoreValue: TextView
    private lateinit var scoreBar: ProgressBar
    private lateinit var scoreHint: TextView

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    /** True while the screen itself is writing switch states, so the listeners ignore those changes. */
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security_settings)
        initViews()
        tts = TextToSpeech(this, this)
        setupListeners()
        loadCurrentStatus()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            tts?.language = Locale("hi", "IN")
        }
    }

    private fun initViews() {
        appLockSwitch = findViewById(R.id.appLockSwitch)
        appLockStatusText = findViewById(R.id.appLockStatusText)
        lockedAppsText = findViewById(R.id.lockedAppsText)
        selectAppsBtn = findViewById(R.id.selectAppsBtn)
        usageStatsBtn = findViewById(R.id.usageStatsBtn)
        usageStatus = findViewById(R.id.usageStatus)
        overlayPermissionBtn = findViewById(R.id.overlayPermissionBtn)
        overlayStatus = findViewById(R.id.overlayStatus)
        fingerprintSwitch = findViewById(R.id.fingerprintSwitch)
        rowFingerprint = findViewById(R.id.rowFingerprint)
        deviceLockSwitch = findViewById(R.id.deviceLockSwitch)
        setPinBtn = findViewById(R.id.setPinBtn)
        pinStatusText = findViewById(R.id.pinStatusText)
        setPatternBtn = findViewById(R.id.setPatternBtn)
        patternStatusText = findViewById(R.id.patternStatusText)
        setVoiceBtn = findViewById(R.id.setVoiceBtn)
        voiceStatusText = findViewById(R.id.voiceStatusText)
        privateModeSwitch = findViewById(R.id.privateModeSwitch)
        privateModeStatusText = findViewById(R.id.privateModeStatusText)
        permissionAnalyzerBtn = findViewById(R.id.permissionAnalyzerBtn)
        networkSecurityBtn = findViewById(R.id.networkSecurityBtn)
        phishingDetectorBtn = findViewById(R.id.phishingDetectorBtn)
        qrScannerBtn = findViewById(R.id.qrScannerBtn)
        quantumDashboardBtn = findViewById(R.id.quantumDashboardBtn)
        scoreTitle = findViewById(R.id.scoreTitle)
        scoreValue = findViewById(R.id.scoreValue)
        scoreBar = findViewById(R.id.scoreBar)
        scoreHint = findViewById(R.id.scoreHint)
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    private fun setPill(view: TextView, ok: Boolean, on: String, off: String) {
        view.text = if (ok) "✓ $on" else off
        view.setTextColor(if (ok) 0xFF3DDC84.toInt() else 0xFFFF6B6B.toInt())
    }

    private fun hasMethod() = SecurityManager.hasPin(this) || PatternManager.isPatternSet(this)

    private fun loadCurrentStatus() {
        refreshing = true
        try {
            val lockOn = SecurityManager.isAppLockEnabled(this) || PatternManager.isPatternLockEnabled(this)
            appLockSwitch.isChecked = lockOn
            appLockStatusText.text = if (lockOn) "App Lock is on" else "App Lock is off"
            appLockStatusText.setTextColor(if (lockOn) 0xFF3DDC84.toInt() else 0xFF8E8E9A.toInt())

            val locked = SecurityManager.getLockedPackages(this).size
            lockedAppsText.text = when (locked) {
                0 -> "No apps selected yet"
                1 -> "1 app locked"
                else -> "$locked apps locked"
            }

            setPill(pinStatusText, SecurityManager.hasPin(this), "Set", "Not set")
            setPill(patternStatusText, PatternManager.isPatternSet(this), "Set", "Not set")
            setPill(voiceStatusText, SecurityManager.hasVoicePassphrase(this), "Set", "Not set")

            val canBiometric = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            if (canBiometric) {
                rowFingerprint.visibility = View.VISIBLE
                fingerprintSwitch.isChecked = SecurityManager.isBiometricEnabled(this)
            } else {
                rowFingerprint.visibility = View.GONE
                SecurityManager.setBiometricEnabled(this, false)
            }

            deviceLockSwitch.isChecked = SecurityManager.isDeviceLockEnabled(this)

            val pm = SecurityManager.isPrivateModeActive(this)
            privateModeSwitch.isChecked = pm
            privateModeStatusText.text = if (pm) "On - chat history is hidden" else "Hide chat history"
            privateModeStatusText.setTextColor(if (pm) 0xFFFFAB40.toInt() else 0xFF8E8E9A.toInt())

            checkPermissions()
            updateScore(lockOn, locked, canBiometric)
        } finally {
            refreshing = false
        }
    }

    /** 0-100 from what is actually protecting the phone, with the single most useful next step. */
    private fun updateScore(lockOn: Boolean, lockedApps: Int, canBiometric: Boolean) {
        var score = 0
        var next: String? = null
        fun step(points: Int, ok: Boolean, hint: String) {
            if (ok) score += points else if (next == null) next = hint
        }
        step(30, lockOn, "Turn on App Lock to protect VoxPilot")
        step(15, SecurityManager.hasPin(this), "Set a PIN as your main unlock")
        step(15, PatternManager.isPatternSet(this), "Add a pattern as a second unlock")
        step(15, !canBiometric || SecurityManager.isBiometricEnabled(this), "Enable fingerprint for quick unlock")
        step(10, SecurityManager.hasVoicePassphrase(this), "Add a voice passphrase")
        step(15, lockedApps > 0, "Pick the apps you want locked")

        val (word, color) = when {
            score >= 70 -> "Strong" to 0xFF3DDC84.toInt()
            score >= 40 -> "Medium" to 0xFFFFAB40.toInt()
            else -> "Weak" to 0xFFFF6B6B.toInt()
        }
        scoreTitle.text = "Protection: $word"
        scoreValue.text = "$score%"
        scoreValue.setTextColor(color)
        scoreBar.progress = score
        scoreHint.text = next ?: "Everything is set up. VoxPilot is well protected."
    }

    private fun setupListeners() {
        appLockSwitch.setOnCheckedChangeListener { _, checked ->
            if (refreshing) return@setOnCheckedChangeListener
            if (checked) {
                if (!hasMethod()) {
                    refreshing = true; appLockSwitch.isChecked = false; refreshing = false
                    toast("Pehle PIN ya Pattern set karo")
                    return@setOnCheckedChangeListener
                }
                SecurityManager.setAppLockEnabled(this, true)
                if (PatternManager.isPatternSet(this)) PatternManager.enablePatternLock(this)
                speak("App lock on ho gaya!")
            } else {
                SecurityManager.setAppLockEnabled(this, false)
                PatternManager.disablePatternLock(this)
                AppLockActivity.isUnlockedThisSession = true
                speak("App lock band kar diya")
            }
            loadCurrentStatus()
        }

        setPinBtn.setOnClickListener { showPinSetupDialog() }
        setPatternBtn.setOnClickListener { onPatternClicked() }
        setVoiceBtn.setOnClickListener { showVoiceSetupDialog() }

        fingerprintSwitch.setOnCheckedChangeListener { _, checked ->
            if (refreshing) return@setOnCheckedChangeListener
            SecurityManager.setBiometricEnabled(this, checked)
            speak(if (checked) "Fingerprint unlock enable ho gaya" else "Fingerprint unlock band kar diya")
            loadCurrentStatus()
        }

        deviceLockSwitch.setOnCheckedChangeListener { _, checked ->
            if (refreshing) return@setOnCheckedChangeListener
            SecurityManager.setDeviceLockEnabled(this, checked)
            speak(if (checked) "System screen lock enable ho gaya" else "System lock band kar diya")
        }

        selectAppsBtn.setOnClickListener { startActivity(Intent(this, AppSelectionActivity::class.java)) }
        usageStatsBtn.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        overlayPermissionBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        permissionAnalyzerBtn.setOnClickListener {
            startActivity(Intent(this, PermissionAnalyzerActivity::class.java))
        }
        networkSecurityBtn.setOnClickListener {
            startActivity(Intent(this, NetworkSecurityActivity::class.java))
        }
        phishingDetectorBtn.setOnClickListener {
            startActivity(Intent(this, PhishingDetectorActivity::class.java))
        }
        qrScannerBtn.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }
        quantumDashboardBtn.setOnClickListener {
            startActivity(Intent(this, QuantumDashboardActivity::class.java))
        }

        privateModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (refreshing) return@setOnCheckedChangeListener
            if (checked) {
                SecurityManager.enablePrivateMode(this)
                speak("Private mode on. Chat history chhup jayegi.")
            } else {
                SecurityManager.disablePrivateMode(this)
                speak("Private mode band.")
            }
            loadCurrentStatus()
        }
    }

    // ── PIN ───────────────────────────────────────────────────

    private fun showPinSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_pin, null)
        val newPinInput = dialogView.findViewById<EditText>(R.id.newPinInput)
        val confPinInput = dialogView.findViewById<EditText>(R.id.confirmPinInput)
        val errorText = dialogView.findViewById<TextView>(R.id.pinErrorText)
        val pinLengthGroup = dialogView.findViewById<android.widget.RadioGroup>(R.id.pinLengthGroup)

        val currentLen = SecurityManager.getPinLength(this)
        when (currentLen) {
            5 -> dialogView.findViewById<android.widget.RadioButton>(R.id.radio5)?.isChecked = true
            6 -> dialogView.findViewById<android.widget.RadioButton>(R.id.radio6)?.isChecked = true
            else -> dialogView.findViewById<android.widget.RadioButton>(R.id.radio4)?.isChecked = true
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (SecurityManager.hasPin(this)) "Change PIN" else "Set PIN")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
        if (SecurityManager.hasPin(this)) builder.setNeutralButton("Remove PIN", null)
        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedId = pinLengthGroup.checkedRadioButtonId
                val pinLength = when (selectedId) {
                    R.id.radio5 -> 5
                    R.id.radio6 -> 6
                    else -> 4
                }

                val pin = newPinInput.text.toString().trim()
                val conf = confPinInput.text.toString().trim()
                when {
                    pin.length != pinLength || !pin.all { it.isDigit() } -> {
                        errorText.text = "PIN must be exactly $pinLength digits"
                        errorText.visibility = View.VISIBLE
                    }
                    pin != conf -> {
                        errorText.text = "PINs do not match"
                        errorText.visibility = View.VISIBLE
                    }
                    else -> {
                        SecurityManager.setPinLength(this, pinLength)
                        SecurityManager.setPin(this, pin)
                        SecurityManager.setAppLockEnabled(this, true)
                        loadCurrentStatus()
                        speak("PIN set ho gaya!")
                        dialog.dismiss()
                    }
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                SecurityManager.removePin(this)
                // Without any unlock method the lock would lock the user out.
                if (!hasMethod()) disableLockCompletely()
                loadCurrentStatus()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun disableLockCompletely() {
        SecurityManager.setAppLockEnabled(this, false)
        PatternManager.disablePatternLock(this)
        AppLockActivity.isUnlockedThisSession = true
    }

    // ── Pattern ───────────────────────────────────────────────

    private fun onPatternClicked() {
        if (!PatternManager.isPatternSet(this)) {
            startPatternSetup()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pattern")
            .setItems(arrayOf("Change pattern", "Remove pattern")) { _, which ->
                if (which == 0) startPatternSetup() else {
                    PatternManager.removePattern(this)
                    if (!hasMethod()) disableLockCompletely()
                    loadCurrentStatus()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startPatternSetup() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, PatternSetupActivity::class.java), 1001)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) loadCurrentStatus()
    }

    // ── Voice passphrase ──────────────────────────────────────

    private fun showVoiceSetupDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_voice_passphrase, null)
        val input = view.findViewById<EditText>(R.id.passphraseInput)
        val recordBtn = view.findViewById<ImageButton>(R.id.recordPassphraseBtn)
        val status = view.findViewById<TextView>(R.id.recordStatusText)
        val preview = view.findViewById<TextView>(R.id.passphrasePreviewText)

        var recognizer: SpeechRecognizer? = null
        val wasMuted = LiveSession.muted

        recordBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                status.text = "Microphone permission is needed - or just type the phrase."
                return@setOnClickListener
            }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                status.text = "Speech recognition isn't available on this phone - type the phrase instead."
                return@setOnClickListener
            }
            LiveSession.setMuted(true) // VoxPilot must not listen to (or answer) the secret phrase
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: AndroidBundle?) { status.text = "Listening... say your phrase" }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { status.text = "Got it..." }
                    override fun onError(error: Int) { status.text = "Couldn't hear that - tap the mic and try again." }
                    override fun onPartialResults(partialResults: AndroidBundle?) {}
                    override fun onEvent(eventType: Int, params: AndroidBundle?) {}
                    override fun onResults(results: AndroidBundle?) {
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (heard.isNotBlank()) {
                            input.setText(heard)
                            input.setSelection(heard.length)
                            preview.text = "Heard: \"$heard\""
                            preview.visibility = View.VISIBLE
                            status.text = "Check it below, then Save."
                        }
                    }
                })
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                })
            }
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Voice passphrase")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
        if (SecurityManager.hasVoicePassphrase(this)) builder.setNeutralButton("Remove", null)
        val dialog = builder.create()
        dialog.setOnDismissListener {
            recognizer?.destroy()
            LiveSession.setMuted(wasMuted)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phrase = input.text.toString().trim()
                // The stored form keeps only a-z / 0-9, so a phrase typed in another script would become empty.
                val usable = phrase.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
                if (usable.length < 4) {
                    status.text = "Use at least a couple of words in English letters / numbers (e.g. \"mera raaz kuch nahi\")."
                    return@setOnClickListener
                }
                SecurityManager.setVoicePassphrase(this, phrase)
                loadCurrentStatus()
                speak("Voice passphrase set ho gaya")
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                SecurityManager.clearVoicePassphrase(this)
                loadCurrentStatus()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ── Permissions ───────────────────────────────────────────

    private fun checkPermissions() {
        setPill(usageStatus, isUsageStatsEnabled(), "Allowed", "Allow")
        setPill(overlayStatus, Settings.canDrawOverlays(this), "Allowed", "Allow")
    }

    private fun isUsageStatsEnabled(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onResume() {
        super.onResume()
        loadCurrentStatus() // coming back from the app picker / permission screens
    }

    private fun speak(text: String) {
        if (isTtsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SEC_TTS_${System.currentTimeMillis()}")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(Intent(context, SecuritySettingsActivity::class.java))
        }
    }
}
