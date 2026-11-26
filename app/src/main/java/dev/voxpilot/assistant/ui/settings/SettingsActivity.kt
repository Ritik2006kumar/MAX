package dev.voxpilot.assistant.ui.settings

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.os.PowerManager
import android.provider.Settings
import android.app.NotificationManager
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.live.LiveSession
import dev.voxpilot.assistant.service.AccessibilityHelperService
import dev.voxpilot.assistant.utils.PermissionUtils
import dev.voxpilot.assistant.utils.Constants
import dev.voxpilot.assistant.service.VoxPilotDeviceAdminReceiver
import dev.voxpilot.assistant.security.SecuritySettingsActivity


class SettingsActivity : AppCompatActivity() {

    // ── Original Views ──────────────────────────────────────────
    private lateinit var apiKeyInput: EditText
    private lateinit var ttsApiKeyInput: EditText
    private lateinit var userNameInput: EditText
    private lateinit var assistantNameInput: EditText
    private lateinit var primeNameInput: EditText
    private lateinit var primeNumberInput: EditText
    private lateinit var voiceTypeGroup: RadioGroup
    private lateinit var liveModeSwitch: CompoundButton
    private lateinit var saveBtn: Button
    private lateinit var accessibilityStatus: TextView
    private lateinit var adminStatusText: TextView
    private lateinit var pickContactBtn: ImageButton

    // ── NEW Views ─────────────────────────────────────────────
    private lateinit var callAnnounceSwitch: CompoundButton
    private lateinit var callAnnounceStatusText: TextView
    private lateinit var messageAnnounceSwitch: CompoundButton
    private lateinit var drivingModeSwitch: CompoundButton
    private lateinit var sleepModeSwitch: CompoundButton
    private lateinit var stealthModeSwitch: CompoundButton
    private lateinit var micMuteSwitch: CompoundButton
    private lateinit var grantPermissionsBtn: Button
    private lateinit var setDefaultAssistantBtn: Button
    private lateinit var permissionsStatusText: TextView

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            handleContactResult(contactUri)
        }
    }

    private val developerImagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val file = java.io.File(filesDir, "developer_profile.png")
                java.io.FileOutputStream(file).use { output ->
                    inputStream?.copyTo(output)
                }
                loadDeveloperPhoto()
                Toast.makeText(this, "Developer photo locked permanently! 🔒", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDeveloperPhoto() {
        val file = java.io.File(filesDir, "developer_profile.png")
        val avatarView = findViewById<ImageView>(R.id.developerAvatar)
        if (file.exists()) {
            try {
                avatarView.setImageURI(android.net.Uri.fromFile(file))
            } catch (_: Exception) {
                avatarView.setImageResource(R.drawable.developer_profile)
            }
        } else {
            avatarView.setImageResource(R.drawable.developer_profile)
        }
    }

    // Required permissions list
    private val allPermissions = PermissionUtils.REQUIRED_PERMISSIONS

    private val personalities = listOf(
        "gf" to "Girlfriend (Sweet & Naughty)",
        "friend" to "Best Friend (Chill & Yaar)",
        "professional" to "Professional (Strict & Formal)",
        "assistant" to "Smart Assistant (Helpful & Direct)",
        "caring" to "Caring (Motherly & Loving)",
        "emotional" to "Emotional (Deeply Attached & Sensitive)",
        "nautanki" to "Nautanki / Dramatic (Filmy & Over-the-top)",
        "sassy" to "Sassy / Witty (Savage & Roaster)",
        "coach" to "Motivational Coach (Strict & High-Energy)",
        "psychology_mentor" to "Psychology & Mind Coach (Mental Mastery)"
    )

    private var currentPersonality = "gf"

    private val voiceModels = listOf(
        "gemini-3.8-live" to "Gemini 3.8 Live - newest, recommended",
        "gemini-3.1-flash-live-preview" to "Gemini 3.1 Flash Live (preview)",
        "gemini-2.5-flash-native-audio-preview-12-2025" to "Gemini 2.5 Native Audio - very expressive",
        "gemini-2.0-flash-exp" to "Gemini 2.0 Flash Live",
    )

    private val voiceNames = listOf(
        "Aoede" to "Breezy", "Kore" to "Firm", "Zephyr" to "Bright", "Leda" to "Youthful",
        "Callirrhoe" to "Easy-going", "Autonoe" to "Bright", "Despina" to "Smooth", "Erinome" to "Clear",
        "Laomedeia" to "Upbeat", "Achernar" to "Soft", "Gacrux" to "Mature", "Pulcherrima" to "Forward",
        "Vindemiatrix" to "Gentle", "Sulafat" to "Warm",
    )

    private var voiceModel = "gemini-3.8-live"
    private var voiceName = "Aoede"
    private val prefs by lazy { getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, VoxPilotDeviceAdminReceiver::class.java)

        initViews()
        loadPreferences()
        setupListeners()
        updateStatus()
    }

    private fun initViews() {
        // Original views
        apiKeyInput = findViewById(R.id.apiKeyInput)
        ttsApiKeyInput = findViewById(R.id.ttsApiKeyInput)
        userNameInput = findViewById(R.id.userNameInput)
        assistantNameInput = findViewById(R.id.assistantNameInput)
        primeNameInput = findViewById(R.id.primeNameInput)
        primeNumberInput = findViewById(R.id.primeNumberInput)
        voiceTypeGroup = findViewById(R.id.voiceTypeGroup)
        liveModeSwitch = findViewById(R.id.liveModeSwitch)
        saveBtn = findViewById(R.id.saveBtn)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        adminStatusText = findViewById(R.id.adminStatusText)
        pickContactBtn = findViewById(R.id.pickContactBtn)

        // NEW views — add these to your XML layout
        callAnnounceSwitch = findViewById(R.id.callAnnounceSwitch)
        callAnnounceStatusText = findViewById(R.id.callAnnounceStatusText)
        messageAnnounceSwitch = findViewById(R.id.messageAnnounceSwitch)
        drivingModeSwitch = findViewById(R.id.drivingModeSwitch)
        sleepModeSwitch = findViewById(R.id.sleepModeSwitch)
        stealthModeSwitch = findViewById(R.id.stealthModeSwitch)
        micMuteSwitch = findViewById(R.id.micMuteSwitch)
        grantPermissionsBtn = findViewById(R.id.grantPermissionsBtn)
        setDefaultAssistantBtn = findViewById(R.id.setDefaultAssistantBtn)
        permissionsStatusText = findViewById(R.id.permissionsStatusText)
    }

    // ── Voice pickers ─────────────────────────────────────────
    private fun showPersonalityPicker() {
        val current = personalities.indexOfFirst { it.first == currentPersonality }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Assistant personality")
            .setSingleChoiceItems(personalities.map { it.second }.toTypedArray(), current) { dialog, which ->
                currentPersonality = personalities[which].first
                findViewById<TextView>(R.id.personalityValue).text = personalities[which].second
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVoiceModelPicker() {
        val current = voiceModels.indexOfFirst { it.first == voiceModel }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Voice model")
            .setSingleChoiceItems(voiceModels.map { it.second }.toTypedArray(), current) { dialog, which ->
                voiceModel = voiceModels[which].first
                findViewById<TextView>(R.id.voiceModelValue).text = voiceModel
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showVoiceNamePicker() {
        val current = voiceNames.indexOfFirst { it.first == voiceName }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("VoxPilot's voice")
            .setSingleChoiceItems(voiceNames.map { "${it.first}  -  ${it.second}" }.toTypedArray(), current) { dialog, which ->
                voiceName = voiceNames[which].first
                findViewById<TextView>(R.id.voiceNameValue).text = voiceName
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Special permissions ────────────────────────────────────
    private fun setPill(view: TextView, ok: Boolean, on: String = "On", off: String = "Off") {
        view.text = if (ok) "✓ $on" else off
        view.setTextColor(if (ok) 0xFF3DDC84.toInt() else 0xFFFF6B6B.toInt())
    }

    private fun openSafely(intent: Intent, fallback: Intent = Intent(Settings.ACTION_SETTINGS)) {
        try { startActivity(intent) } catch (e: Exception) {
            try { startActivity(fallback) } catch (_: Exception) {
                Toast.makeText(this, "Settings open nahi ho paya", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpecialPermissionRows() {
        findViewById<View>(R.id.rowOverlay).setOnClickListener {
            openSafely(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<View>(R.id.rowWriteSettings).setOnClickListener {
            openSafely(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<View>(R.id.rowDnd).setOnClickListener {
            openSafely(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        findViewById<View>(R.id.rowBattery).setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Already unrestricted ✅", Toast.LENGTH_SHORT).show()
            } else {
                openSafely(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            }
        }
        findViewById<View>(R.id.rowVoiceModel).setOnClickListener { showVoiceModelPicker() }
        findViewById<View>(R.id.rowVoiceName).setOnClickListener { showVoiceNamePicker() }
        findViewById<View>(R.id.rowPersonality).setOnClickListener { showPersonalityPicker() }
        findViewById<View>(R.id.rowTouchGuard).setOnClickListener {
            startActivity(Intent(this, dev.voxpilot.assistant.security.TouchGuardActivity::class.java))
        }
        findViewById<View>(R.id.rowMotionGuard).setOnClickListener {
            startActivity(Intent(this, dev.voxpilot.assistant.security.MotionGuardActivity::class.java))
        }
        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }
    }

    private fun updateSpecialStatuses() {
        setPill(findViewById(R.id.overlayStatus), Settings.canDrawOverlays(this), "Allowed", "Not allowed")
        setPill(findViewById(R.id.writeStatus), Settings.System.canWrite(this), "Allowed", "Not allowed")
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        setPill(findViewById(R.id.dndStatus), nm.isNotificationPolicyAccessGranted, "Allowed", "Not allowed")
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        setPill(findViewById(R.id.batteryStatus), pm.isIgnoringBatteryOptimizations(packageName), "Unrestricted", "Restricted")
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        ttsApiKeyInput.setText(prefs.getString("tts_api_key", ""))
        userNameInput.setText(prefs.getString("user_name", "Sir"))
        assistantNameInput.setText(prefs.getString(Constants.KEY_ASSISTANT_NAME, "Max"))
        primeNameInput.setText(prefs.getString("prime_name", ""))
        primeNumberInput.setText(prefs.getString("prime_number", ""))

        currentPersonality = prefs.getString("personality_mode", "gf") ?: "gf"
        val pLabel = personalities.find { it.first == currentPersonality }?.second ?: personalities[0].second
        findViewById<TextView>(R.id.personalityValue).text = pLabel

        when (prefs.getString("voice_engine", "system")) {
            "elevenlabs" -> findViewById<RadioButton>(R.id.radioEleven).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioSystem).isChecked = true
        }

        val gender = prefs.getString("voice_gender", "female")
        when (gender) {
            "jarvis" -> findViewById<RadioButton>(R.id.radioJarvis)?.isChecked = true
            "male" -> findViewById<RadioButton>(R.id.radioMale)?.isChecked = true
            else -> findViewById<RadioButton>(R.id.radioFemale)?.isChecked = true
        }
        findViewById<EditText>(R.id.voiceIdInput)?.setText(prefs.getString("elevenlabs_voice_id", ""))

        liveModeSwitch.isChecked = prefs.getBoolean("live_mode_enabled", false)

        voiceModel = prefs.getString("voice_model", voiceModels[0].first) ?: voiceModels[0].first
        voiceName = prefs.getString("voice_name", "Aoede") ?: "Aoede"
        findViewById<TextView>(R.id.voiceModelValue).text = voiceModel
        findViewById<TextView>(R.id.voiceNameValue).text = voiceName
        findViewById<SeekBar>(R.id.volumeSeek).progress = (prefs.getFloat("voice_volume", 1f) * 100).toInt()

        // Call announce (default ON for better UX)
        val announceEnabled = prefs.getBoolean("call_announce_enabled", true)
        callAnnounceSwitch.isChecked = announceEnabled
        updateCallAnnounceStatus(announceEnabled)

        drivingModeSwitch.isChecked = prefs.getBoolean(Constants.KEY_DRIVING_MODE, false)
        sleepModeSwitch.isChecked = prefs.getBoolean(Constants.KEY_SLEEP_MODE, false)
        stealthModeSwitch.isChecked = prefs.getBoolean(Constants.KEY_STEALTH_MODE, false)
        micMuteSwitch.isChecked = prefs.getBoolean(Constants.KEY_MIC_MUTED, false)
        messageAnnounceSwitch.isChecked = prefs.getBoolean(Constants.KEY_MESSAGE_ANNOUNCE, true)
    }

    private fun setupListeners() {
        saveBtn.setOnClickListener { savePreferences() }
        setupSpecialPermissionRows()

        findViewById<View>(R.id.appIconStudioCard)?.setOnClickListener {
            startActivity(Intent(this, AppIconCustomizerActivity::class.java))
        }

        findViewById<View>(R.id.installedAppsCard)?.setOnClickListener {
            startActivity(Intent(this, InstalledAppsActivity::class.java))
        }

        findViewById<View>(R.id.setDefaultHomeBtnCard)?.setOnClickListener {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent(Settings.ACTION_HOME_SETTINGS)
            } else {
                Intent("android.settings.HOME_SETTINGS")
            }
            openSafely(intent)
        }

        pickContactBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        }

        loadDeveloperPhoto()
        findViewById<ImageView>(R.id.developerAvatar)?.setOnClickListener {
            val file = java.io.File(filesDir, "developer_profile.png")
            if (!file.exists()) {
                developerImagePickerLauncher.launch("image/*")
            } else {
                Toast.makeText(this, "Developer photo is locked permanently! 🔒", Toast.LENGTH_SHORT).show()
            }
        }

        // Original cards
        findViewById<View>(R.id.accessibilityCard).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<View>(R.id.deviceAdminCard).setOnClickListener {
            if (!devicePolicyManager.isAdminActive(componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "VoxPilot needs admin to control system.")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Admin is already active ✅", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.securitySettingsCard).setOnClickListener {
            startActivity(Intent(this, SecuritySettingsActivity::class.java))
        }

        findViewById<View>(R.id.clearMemoryCard)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Conversation Memory?")
                .setMessage("Kya aap poori conversation history aur memories delete karna chahte hain?")
                .setPositiveButton("Clear Memory") { _, _ ->
                    dev.voxpilot.assistant.ai.ConversationMemory.clear(this)
                    Toast.makeText(this, "Conversation memory cleared! 🧹", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // NEW: Call announce switch
        callAnnounceSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateCallAnnounceStatus(isChecked)
        }

        // NEW: Grant permissions button
        grantPermissionsBtn.setOnClickListener {
            checkAndRequestPermissions()
        }

        // NEW: Set default assistant button
        setDefaultAssistantBtn.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                Toast.makeText(this, "VoxPilot ko Default Assistant chuno 👆", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Settings → Apps → Default Apps → Assistant → VoxPilot", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * NEW: Check and request all required permissions
     */
    private fun checkAndRequestPermissions() {
        val missing = allPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            Toast.makeText(this, "Sab permissions already granted! ✅", Toast.LENGTH_SHORT).show()
            updatePermissionsStatus()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val granted = grantResults.count { it == PackageManager.PERMISSION_GRANTED }
            val total = permissions.size

            if (granted == total) {
                Toast.makeText(this, "Sab permissions mil gayi! ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "$granted/$total permissions mili ⚠️", Toast.LENGTH_LONG).show()

                val permanentlyDenied = permissions.filterIndexed { index, _ ->
                    grantResults[index] == PackageManager.PERMISSION_DENIED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[index])
                }

                if (permanentlyDenied.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("Permissions Required ⚠️")
                        .setMessage("Kuch permissions permanently deny ho gayi hain. Settings se manually enable karo.\n\n" +
                                permanentlyDenied.joinToString("\n") { "• ${it.split('.').last()}" })
                        .setPositiveButton("Open Settings") { _, _ ->
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this, "Settings open nahi ho paya", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            updatePermissionsStatus()
        }
    }

    private fun updateCallAnnounceStatus(enabled: Boolean) {
        callAnnounceStatusText.text = if (enabled) "VoxPilot tells you who is calling" else "Call announce is off"
    }

    private fun updatePermissionsStatus() {
        val missing = allPermissions.count {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        permissionsStatusText.text = when {
            missing == 0 -> "✓ All granted"
            else -> "$missing missing"
        }
        permissionsStatusText.setTextColor(when {
            missing == 0 -> 0xFF00E676.toInt()
            missing <= 2 -> 0xFFFFA726.toInt()
            else -> 0xFFFF1744.toInt()
        })
    }

    private fun handleContactResult(uri: android.net.Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        if (cursor?.moveToFirst() == true) {
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            primeNameInput.setText(cursor.getString(nameIndex))
            primeNumberInput.setText(cursor.getString(numIndex).replace(" ", ""))
        }
        cursor?.close()
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE).edit()

        prefs.putString("api_key", apiKeyInput.text.toString().trim())
        prefs.putString("tts_api_key", ttsApiKeyInput.text.toString().trim())
        prefs.putString("user_name", userNameInput.text.toString().trim())
        val assistantName = assistantNameInput.text.toString().trim().ifBlank { "Max" }
        prefs.putString(Constants.KEY_ASSISTANT_NAME, assistantName)
        prefs.putString(Constants.KEY_WAKE_WORD, assistantName)
        prefs.putString("prime_name", primeNameInput.text.toString().trim())
        prefs.putString("prime_number", primeNumberInput.text.toString().trim())

        prefs.putString("personality_mode", currentPersonality)

        val voiceEngine = when (voiceTypeGroup.checkedRadioButtonId) {
            R.id.radioEleven -> "elevenlabs"
            else -> "system"
        }
        prefs.putString("voice_engine", voiceEngine)

        val voiceGenderGroup = findViewById<RadioGroup>(R.id.voiceGenderGroup)
        val customVoiceId = findViewById<EditText>(R.id.voiceIdInput)?.text?.toString()?.trim().orEmpty()

        val gender = when (voiceGenderGroup?.checkedRadioButtonId) {
            R.id.radioJarvis -> "jarvis"
            R.id.radioMale -> "male"
            else -> "female"
        }
        prefs.putString("voice_gender", gender)

        if (customVoiceId.isNotBlank()) {
            prefs.putString("elevenlabs_voice_id", customVoiceId)
            prefs.putString("voice_name", if (gender == "female") "Aoede" else "Charon")
        } else if (gender == "jarvis") {
            prefs.putString("voice_name", "Charon")
            prefs.putString("elevenlabs_voice_id", "JBFqnCBsd6RMkjVDRZzb")
        } else if (gender == "male") {
            prefs.putString("voice_name", "Puck")
            prefs.putString("elevenlabs_voice_id", "pNInz6obpgDQGcFmaJgB")
        } else {
            prefs.putString("voice_name", "Aoede")
            prefs.putString("elevenlabs_voice_id", "21m00Tcm4TlvDq8ikWAM")
        }

        prefs.putBoolean("live_mode_enabled", liveModeSwitch.isChecked)
        prefs.putBoolean("call_announce_enabled", callAnnounceSwitch.isChecked)
        prefs.putBoolean(Constants.KEY_DRIVING_MODE, drivingModeSwitch.isChecked)
        prefs.putBoolean(Constants.KEY_SLEEP_MODE, sleepModeSwitch.isChecked)
        prefs.putBoolean(Constants.KEY_STEALTH_MODE, stealthModeSwitch.isChecked)
        prefs.putBoolean(Constants.KEY_MESSAGE_ANNOUNCE, messageAnnounceSwitch.isChecked)
        val micMuted = micMuteSwitch.isChecked
        prefs.putBoolean(Constants.KEY_MIC_MUTED, micMuted)
        LiveSession.setMuted(micMuted)

        prefs.putString("voice_model", voiceModel)
        prefs.putString("voice_name", voiceName)
        prefs.putFloat("voice_volume", findViewById<SeekBar>(R.id.volumeSeek).progress / 100f)

        prefs.apply()
        // A new voice / model / personality only takes effect on a fresh live session.
        if (LiveSession.isActive) LiveSession.reconnect()
        Toast.makeText(this, "Settings saved ✅", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        setPill(accessibilityStatus, enabled, "Full control", "Turn on")

        val adminActive = devicePolicyManager.isAdminActive(componentName)
        setPill(adminStatusText, adminActive, "Active", "Inactive")

        updatePermissionsStatus()
        updateSpecialStatuses()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 200
    }
}