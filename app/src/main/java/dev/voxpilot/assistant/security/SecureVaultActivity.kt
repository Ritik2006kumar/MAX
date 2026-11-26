package dev.voxpilot.assistant.security

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.utils.CameraSnapshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SecureVaultActivity : AppCompatActivity() {

    private lateinit var vaultListView: ListView
    private lateinit var emptyView: TextView
    private lateinit var addNoteBtn: Button
    private lateinit var addMediaBtn: Button
    private lateinit var intrudersBtn: Button

    private val itemList = mutableListOf<SecureVaultManager.VaultItem>()
    private lateinit var adapter: VaultAdapter
    private var isUnlocked = false
    private var wrongPinAttempts = 0

    private val mediaPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            saveSelectedMedia(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. FLAG_SECURE: Prevent screenshots, screen recording, and recents preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_secure_vault)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        vaultListView = findViewById(R.id.vaultListView)
        emptyView = findViewById(R.id.emptyView)
        addNoteBtn = findViewById(R.id.addNoteBtn)
        addMediaBtn = findViewById(R.id.addMediaBtn)
        intrudersBtn = findViewById(R.id.intrudersBtn)

        adapter = VaultAdapter(this, itemList)
        vaultListView.adapter = adapter

        addNoteBtn.setOnClickListener { showAddNoteDialog() }
        addMediaBtn.setOnClickListener { mediaPickerLauncher.launch("*/*") }
        intrudersBtn.setOnClickListener { showIntrudersDialog() }

        // Request camera permission for intruder selfie capture
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }

        vaultListView.setOnItemClickListener { _, _, position, _ ->
            val item = itemList[position]
            if (item.type == "NOTE") {
                val decrypted = SecurityManager.decrypt(item.pathOrContent)
                AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setMessage("Decrypted Content:\n$decrypted")
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Delete Note") { _, _ ->
                        SecureVaultManager.deleteItem(this, item)
                        loadVault()
                        Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } else {
                val file = File(item.pathOrContent)
                if (file.exists()) {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, if (item.title.lowercase().contains("video") || file.absolutePath.endsWith(".mp4", true)) "video/*" else "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open media: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Media file not found on disk", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isUnlocked) {
            checkAuthenticationAndPin()
        }
    }

    override fun onPause() {
        super.onPause()
        // 2. Instant Auto-Lock on Background
        isUnlocked = false
    }

    private fun checkAuthenticationAndPin() {
        if (!SecureVaultManager.hasVaultPin(this)) {
            showSetupPinDialog()
        } else {
            BiometricManager.authenticate(
                this,
                onSuccess = {
                    isUnlocked = true
                    loadVault()
                },
                onError = { showPinPromptDialog() },
                onFallback = { showPinPromptDialog() }
            )
        }
    }

    private fun showSetupPinDialog() {
        val input = EditText(this).apply {
            hint = "Enter 4/6-Digit Vault PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888899.toInt())
        }

        AlertDialog.Builder(this)
            .setTitle("Set Vault Security PIN")
            .setMessage("Create a dedicated PIN to protect your private vault.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Set PIN") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length >= 4) {
                    SecureVaultManager.setVaultPin(this, pin)
                    isUnlocked = true
                    loadVault()
                    Toast.makeText(this, "Vault PIN created successfully 🔒", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    showSetupPinDialog()
                }
            }
            .show()
    }

    private fun showPinPromptDialog() {
        val input = EditText(this).apply {
            hint = "Enter Vault PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888899.toInt())
        }

        AlertDialog.Builder(this)
            .setTitle("Vault Locked")
            .setMessage("Enter your custom Vault PIN (3 incorrect attempts will capture intruder selfie):")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Unlock") { _, _ ->
                val pin = input.text.toString().trim()
                if (SecureVaultManager.verifyVaultPin(this, pin)) {
                    isUnlocked = true
                    wrongPinAttempts = 0
                    loadVault()
                } else {
                    wrongPinAttempts++
                    if (wrongPinAttempts >= 3) {
                        captureIntruderPhoto()
                        wrongPinAttempts = 0
                    }
                    Toast.makeText(this, "Incorrect PIN! Attempt $wrongPinAttempts/3", Toast.LENGTH_SHORT).show()
                    showPinPromptDialog()
                }
            }
            .setNegativeButton("Retry Biometric") { _, _ ->
                checkAuthenticationAndPin()
            }
            .show()
    }

    private fun captureIntruderPhoto() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val photoFile = CameraSnapshotHelper.capturePhoto(this@SecureVaultActivity)
                if (photoFile != null) {
                    SecureVaultManager.logIntruder(this@SecureVaultActivity, photoFile.absolutePath)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SecureVaultActivity, "🚨 Intruder photo captured successfully!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showIntrudersDialog() {
        val intruders = SecureVaultManager.getIntruders(this)
        if (intruders.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Intruder Log")
                .setMessage("No intruder selfies captured yet. (Triggered after 3 wrong PIN attempts)")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val names = intruders.map { "Intruder Capture @ ${java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp))}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Intruder Selfie Log (${intruders.size})")
            .setItems(names) { _, which ->
                val intruder = intruders[which]
                val file = File(intruder.imagePath)
                if (file.exists()) {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open photo: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Photo file no longer exists", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun saveSelectedMedia(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val mediaDir = File(filesDir, "vault_media").apply { if (!exists()) mkdirs() }
            val fileName = "encrypted_media_${System.currentTimeMillis()}"
            val destFile = File(mediaDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val title = "Private Media ${System.currentTimeMillis().toString().takeLast(4)}"
            SecureVaultManager.saveItem(this, title, "MEDIA", destFile.absolutePath)
            loadVault()
            Toast.makeText(this, "Private photo/video secured in vault 🔒", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save media: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadVault() {
        itemList.clear()
        itemList.addAll(SecureVaultManager.getItems(this))
        adapter.notifyDataSetChanged()

        if (itemList.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            vaultListView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            vaultListView.visibility = View.VISIBLE
        }
    }

    private fun showAddNoteDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val titleInput = EditText(this).apply {
            hint = "Note Title"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888899.toInt())
        }

        val contentInput = EditText(this).apply {
            hint = "Secret Note / Password Content..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888899.toInt())
            minLines = 4
        }

        layout.addView(titleInput)
        layout.addView(contentInput)

        AlertDialog.Builder(this)
            .setTitle("Add Encrypted Note")
            .setView(layout)
            .setPositiveButton("Save Securely") { _, _ ->
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (title.isNotEmpty() && content.isNotEmpty()) {
                    SecureVaultManager.saveItem(this, title, "NOTE", content)
                    loadVault()
                    Toast.makeText(this, "Note encrypted and saved with AES-256 🔒", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class VaultAdapter(context: Context, private val items: List<SecureVaultManager.VaultItem>) :
        ArrayAdapter<SecureVaultManager.VaultItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_permission_app, parent, false)
            val item = items[position]

            val nameView = view.findViewById<TextView>(R.id.appName)
            val packageView = view.findViewById<TextView>(R.id.appPackage)
            val summaryView = view.findViewById<TextView>(R.id.appSummary)
            val riskBadge = view.findViewById<TextView>(R.id.riskBadge)
            val iconView = view.findViewById<ImageView>(R.id.appIcon)

            nameView.text = item.title
            if (item.type == "NOTE") {
                packageView.text = "AES-256 Encrypted Vault Note"
                summaryView.text = "Tap to decrypt and read text"
                iconView.setImageResource(android.R.drawable.ic_lock_lock)
            } else {
                packageView.text = "Protected Private Media File"
                summaryView.text = "Tap to view photo or video securely"
                iconView.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            riskBadge.text = "SECURE"
            riskBadge.setTextColor(0xFF3DDC84.toInt())
            riskBadge.setBackgroundColor(0x333DDC84)

            return view
        }
    }
}
