package dev.voxpilot.assistant.security

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.ui.main.MainActivity
import dev.voxpilot.assistant.ui.settings.SettingsActivity
import dev.voxpilot.assistant.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class QuantumDashboardActivity : AppCompatActivity() {

    private var dashboardAssistantName: TextView? = null
    private var dashboardGreetingText: TextView? = null
    private var batteryPercentText: TextView? = null
    private var batteryStatusText: TextView? = null
    private var batteryTimeText: TextView? = null
    private var ramPercentText: TextView? = null
    private var ramProgressBar: ProgressBar? = null
    private var ramDetailText: TextView? = null
    private var storagePercentText: TextView? = null
    private var storageProgressBar: ProgressBar? = null
    private var storageDetailText: TextView? = null
    private var wifiSsidText: TextView? = null
    private var mobileCarrierText: TextView? = null
    private var mobileNetworkGenBadge: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quantum_dashboard)

        dashboardAssistantName = findViewById(R.id.dashboardAssistantName)
        dashboardGreetingText = findViewById(R.id.dashboardGreetingText)
        batteryPercentText = findViewById(R.id.batteryPercentText)
        batteryStatusText = findViewById(R.id.batteryStatusText)
        batteryTimeText = findViewById(R.id.batteryTimeText)
        ramPercentText = findViewById(R.id.ramPercentText)
        ramProgressBar = findViewById(R.id.ramProgressBar)
        ramDetailText = findViewById(R.id.ramDetailText)
        storagePercentText = findViewById(R.id.storagePercentText)
        storageProgressBar = findViewById(R.id.storageProgressBar)
        storageDetailText = findViewById(R.id.storageDetailText)
        wifiSsidText = findViewById(R.id.wifiSsidText)
        mobileCarrierText = findViewById(R.id.mobileCarrierText)
        mobileNetworkGenBadge = findViewById(R.id.mobileNetworkGenBadge)

        findViewById<View>(R.id.cardInstalledApps)?.setOnClickListener {
            startActivity(Intent(this, dev.voxpilot.assistant.ui.settings.InstalledAppsActivity::class.java))
        }

        findViewById<View>(R.id.cardApkScanner)?.setOnClickListener {
            startActivity(Intent(this, ApkScannerActivity::class.java))
        }

        findViewById<View>(R.id.cardConnectionEndpoints)?.setOnClickListener {
            startActivity(Intent(this, ConnectionEndpointActivity::class.java))
        }

        findViewById<View>(R.id.cardSecureVault)?.setOnClickListener {
            startActivity(Intent(this, SecureVaultActivity::class.java))
        }

        findViewById<View>(R.id.cardBatteryDrain)?.setOnClickListener {
            startActivity(Intent(this, BatteryDrainActivity::class.java))
        }

        findViewById<View>(R.id.cardPhishingGuard)?.setOnClickListener {
            startActivity(Intent(this, PhishingGuardActivity::class.java))
        }

        findViewById<View>(R.id.cardPermissionAnalyzer)?.setOnClickListener {
            startActivity(Intent(this, PermissionAnalyzerActivity::class.java))
        }

        findViewById<View>(R.id.cardNetworkSecurity)?.setOnClickListener {
            startActivity(Intent(this, NetworkSecurityActivity::class.java))
        }

        findViewById<View>(R.id.cardPhishing)?.setOnClickListener {
            startActivity(Intent(this, PhishingDetectorActivity::class.java))
        }

        findViewById<View>(R.id.cardQrScanner)?.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }

        // Bottom Dock Actions
        findViewById<View>(R.id.dockAppGridBtn)?.setOnClickListener {
            startActivity(Intent(this, dev.voxpilot.assistant.ui.settings.InstalledAppsActivity::class.java))
        }
        findViewById<View>(R.id.dockMicOrbBtn)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.dockSettingsBtn)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        loadDeviceMetrics()
    }

    override fun onResume() {
        super.onResume()
        loadDeviceMetrics()
    }

    private fun loadDeviceMetrics() {
        val prefs = getSharedPreferences("voxpilot_prefs", MODE_PRIVATE)
        val assistantName = prefs.getString(Constants.KEY_ASSISTANT_NAME, "MYRA")?.uppercase() ?: "MYRA"
        val userName = prefs.getString(Constants.KEY_USER_NAME, "Sir") ?: "Sir"

        dashboardAssistantName?.text = assistantName
        dashboardGreetingText?.text = " Hello $userName!"

        // Load hardware metrics in background thread to prevent ANR / "Process system isn't responding"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Battery
                val bm = getSystemService(BATTERY_SERVICE) as? BatteryManager
                val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 80

                val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                val batStatusStr = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "⚡ Charging"
                    BatteryManager.BATTERY_STATUS_FULL -> "✓ Fully Charged"
                    else -> "⚡ Active"
                }

                val batTimeStr = if (isCharging) {
                    if (status == BatteryManager.BATTERY_STATUS_FULL) "Battery Full" else "Charging to 100%"
                } else {
                    val totalMins = (batteryPct * 11 * 60) / 100
                    val hrs = totalMins / 60
                    val mins = totalMins % 60
                    if (hrs > 0) "≈ ${hrs}h ${mins}m Left" else "≈ ${mins}m Left"
                }

                // RAM
                val actManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager?.getMemoryInfo(memInfo)
                val availMb = memInfo.availMem / (1024 * 1024)
                val totalMb = memInfo.totalMem / (1024 * 1024)
                val usedMb = (totalMb - availMb).coerceAtLeast(0)
                val ramPct = if (totalMb > 0) ((usedMb * 100) / totalMb).toInt() else 50
                val ramDetailStr = String.format(Locale.ROOT, "%.1f GB / %.1f GB", usedMb / 1024.0, totalMb / 1024.0)

                // Storage
                val stat = StatFs(Environment.getDataDirectory().path)
                val totalBytes = stat.totalBytes
                val availableBytes = stat.availableBytes
                val usedBytes = (totalBytes - availableBytes).coerceAtLeast(0)
                val storagePct = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 50
                val storageDetailStr = String.format(Locale.ROOT, "%.1f GB / %.1f GB", usedBytes / (1024.0 * 1024 * 1024), totalBytes / (1024.0 * 1024 * 1024))

                // Network / Wi-Fi
                val cm = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
                val isWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                val isCellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true

                var wifiDetail = "Disconnected"
                if (isWifi) {
                    try {
                        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                        val wifiInfo = wm?.connectionInfo
                        if (wifiInfo != null) {
                            val rssi = wifiInfo.rssi
                            val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                wm.calculateSignalLevel(rssi)
                            } else {
                                @Suppress("DEPRECATION")
                                WifiManager.calculateSignalLevel(rssi, 5)
                            }
                            val ssidClean = wifiInfo.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" && it.isNotBlank() }
                            val signalPercent = ((rssi + 100).coerceIn(0, 50) * 2).coerceIn(0, 100)
                            wifiDetail = if (ssidClean != null) {
                                "$ssidClean • Signal $signalPercent% (Level $level/4)"
                            } else {
                                "Wi-Fi • Signal $signalPercent% (Level $level/4)"
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Mobile Network
                var carrierTextStr = if (isCellular) "Active Cellular" else "Mobile Data Off"
                var genStr = "4G"
                try {
                    @Suppress("MissingPermission")
                    val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val carrierName = try { tm?.networkOperatorName?.takeIf { it.isNotBlank() } ?: tm?.simOperatorName?.takeIf { it.isNotBlank() } } catch (_: Exception) { null } ?: "Mobile Data"

                    val netType = try {
                        @Suppress("MissingPermission")
                        tm?.dataNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
                    } catch (_: Exception) {
                        TelephonyManager.NETWORK_TYPE_UNKNOWN
                    }

                    genStr = when (netType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                        TelephonyManager.NETWORK_TYPE_HSPAP,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                        else -> "4G/5G"
                    }

                    if (isCellular) {
                        carrierTextStr = "$carrierName $genStr"
                    }
                } catch (_: Exception) {}

                withContext(Dispatchers.Main) {
                    batteryPercentText?.text = "$batteryPct%"
                    batteryStatusText?.text = batStatusStr
                    batteryTimeText?.text = batTimeStr
                    ramPercentText?.text = "$ramPct%"
                    ramProgressBar?.progress = ramPct
                    ramDetailText?.text = ramDetailStr
                    storagePercentText?.text = "$storagePct%"
                    storageProgressBar?.progress = storagePct
                    storageDetailText?.text = storageDetailStr
                    wifiSsidText?.text = wifiDetail
                    mobileNetworkGenBadge?.text = genStr
                    mobileCarrierText?.text = carrierTextStr
                }
            } catch (_: Exception) {}
        }
    }
}
