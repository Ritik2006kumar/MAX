package dev.voxpilot.assistant.security

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class PermissionDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_detail)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        val packageName = intent.getStringExtra("package_name") ?: run {
            finish()
            return
        }

        loadAppData(packageName)
    }

    private fun loadAppData(packageName: String) {
        val pm = packageManager
        val pkgInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "App details not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val appInfo = pkgInfo.applicationInfo ?: run {
            Toast.makeText(this, "App info not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { packageName }
        val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { getDrawable(android.R.drawable.sym_def_app_icon)!! }
        val versionName = pkgInfo.versionName ?: "1.0"

        val iconView = findViewById<ImageView>(R.id.detailIcon)
        val nameView = findViewById<TextView>(R.id.detailName)
        val packageView = findViewById<TextView>(R.id.detailPackage)
        val versionView = findViewById<TextView>(R.id.detailVersion)
        val riskBadge = findViewById<TextView>(R.id.detailRiskBadge)
        val riskExplanation = findViewById<TextView>(R.id.detailRiskExplanation)
        val permissionsContainer = findViewById<LinearLayout>(R.id.permissionsContainer)
        val openSettingsBtn = findViewById<Button>(R.id.openSettingsBtn)

        iconView.setImageDrawable(icon)
        nameView.text = appName
        packageView.text = packageName
        versionView.text = "Version: $versionName"

        val requestedPerms = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        val requestedFlags = pkgInfo.requestedPermissionsFlags ?: intArrayOf()

        val grantedPerms = mutableListOf<String>()

        for (i in requestedPerms.indices) {
            val perm = requestedPerms[i]
            val granted = i < requestedFlags.size && (requestedFlags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            if (granted) {
                grantedPerms.add(perm)
            }
        }

        val sensitivePermissions = setOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS
        )

        val sensitiveGrantedCount = grantedPerms.count { it in sensitivePermissions }

        val riskLevel = when {
            sensitiveGrantedCount >= 3 -> PermissionAnalyzerActivity.RiskLevel.HIGH
            sensitiveGrantedCount >= 1 -> PermissionAnalyzerActivity.RiskLevel.MEDIUM
            else -> PermissionAnalyzerActivity.RiskLevel.LOW
        }

        when (riskLevel) {
            PermissionAnalyzerActivity.RiskLevel.HIGH -> {
                riskBadge.text = "HIGH RISK"
                riskBadge.setTextColor(0xFFFF6B6B.toInt())
                riskBadge.setBackgroundColor(0x33FF6B6B)
                riskExplanation.text = "This application holds $sensitiveGrantedCount sensitive permissions (such as Camera, Mic, Location, SMS, or Phone). Review whether these permissions are necessary for its intended functionality."
            }
            PermissionAnalyzerActivity.RiskLevel.MEDIUM -> {
                riskBadge.text = "MEDIUM RISK"
                riskBadge.setTextColor(0xFFFFAB40.toInt())
                riskBadge.setBackgroundColor(0x33FFAB40)
                riskExplanation.text = "This application holds $sensitiveGrantedCount sensitive permission. Ensure you trust this application and understand why it requires sensitive access."
            }
            PermissionAnalyzerActivity.RiskLevel.LOW -> {
                riskBadge.text = "LOW RISK"
                riskBadge.setTextColor(0xFF3DDC84.toInt())
                riskBadge.setBackgroundColor(0x333DDC84)
                riskExplanation.text = "This application requests minimal or no sensitive permissions. It presents a low privacy risk profile."
            }
        }

        // Categorize permissions
        val categories = mapOf(
            "📷 Camera" to listOf(android.Manifest.permission.CAMERA),
            "🎙️ Microphone" to listOf(android.Manifest.permission.RECORD_AUDIO),
            "📍 Location" to listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            "📇 Contacts" to listOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS, android.Manifest.permission.GET_ACCOUNTS),
            "💬 SMS" to listOf(android.Manifest.permission.READ_SMS, android.Manifest.permission.SEND_SMS, android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.RECEIVE_MMS),
            "📞 Phone & Call" to listOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.CALL_PHONE, android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.ANSWER_PHONE_CALLS),
            "💾 Storage" to listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO),
            "🔔 Notifications" to listOf(android.Manifest.permission.POST_NOTIFICATIONS)
        )

        permissionsContainer.removeAllViews()

        for ((categoryName, catPerms) in categories) {
            val appPermsInCategory = requestedPerms.filter { p -> catPerms.any { p.contains(it) } }
            if (appPermsInCategory.isNotEmpty()) {
                val catHeader = TextView(this).apply {
                    text = "$categoryName (${appPermsInCategory.size})"
                    setTextColor(0xFFFF2D55.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 16, 0, 4)
                }
                permissionsContainer.addView(catHeader)

                for (p in appPermsInCategory) {
                    val isGranted = p in grantedPerms
                    val permView = TextView(this).apply {
                        text = " • ${p.substringAfterLast('.')} (${if (isGranted) "Granted" else "Denied"})"
                        setTextColor(if (isGranted) 0xFFFFFFFF.toInt() else 0xFF777777.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding(12, 2, 0, 2)
                    }
                    permissionsContainer.addView(permView)
                }
            }
        }

        // Other permissions
        val categorizedSet = categories.values.flatten()
        val otherPerms = requestedPerms.filter { p -> categorizedSet.none { p.contains(it) } }
        if (otherPerms.isNotEmpty()) {
            val catHeader = TextView(this).apply {
                text = "⚡ Other Permissions (${otherPerms.size})"
                setTextColor(0xFF8E8E9A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 4)
            }
            permissionsContainer.addView(catHeader)

            for (p in otherPerms) {
                val isGranted = p in grantedPerms
                val permView = TextView(this).apply {
                    text = " • ${p.substringAfterLast('.')} (${if (isGranted) "Granted" else "Denied"})"
                    setTextColor(if (isGranted) 0xFFBBBBBB.toInt() else 0xFF555555.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(12, 2, 0, 2)
                }
                permissionsContainer.addView(permView)
            }
        }

        if (requestedPerms.isEmpty()) {
            val noPerms = TextView(this).apply {
                text = "No permissions requested by this app."
                setTextColor(0xFF8E8E9A.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, 12, 0, 12)
            }
            permissionsContainer.addView(noPerms)
        }

        openSettingsBtn.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
