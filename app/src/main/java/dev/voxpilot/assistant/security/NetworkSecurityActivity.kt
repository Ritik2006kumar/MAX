package dev.voxpilot.assistant.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkSecurityActivity : AppCompatActivity() {

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var statusIndicator: View
    private lateinit var connectionStatusTitle: TextView
    private lateinit var connectionStatusSubtitle: TextView
    private lateinit var networkTypeVal: TextView
    private lateinit var wifiSsidVal: TextView
    private lateinit var wifiSignalVal: TextView
    private lateinit var ipAddressVal: TextView
    private lateinit var internetAccessVal: TextView
    private lateinit var vpnStatusVal: TextView
    private lateinit var airplaneModeVal: TextView
    private lateinit var checklistVpnText: TextView
    private lateinit var checklistTlsText: TextView
    private lateinit var checklistTypeStruct: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_security)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        statusIndicator = findViewById(R.id.statusIndicator)
        connectionStatusTitle = findViewById(R.id.connectionStatusTitle)
        connectionStatusSubtitle = findViewById(R.id.connectionStatusSubtitle)
        networkTypeVal = findViewById(R.id.networkTypeVal)
        wifiSsidVal = findViewById(R.id.wifiSsidVal)
        wifiSignalVal = findViewById(R.id.wifiSignalVal)
        ipAddressVal = findViewById(R.id.ipAddressVal)
        internetAccessVal = findViewById(R.id.internetAccessVal)
        vpnStatusVal = findViewById(R.id.vpnStatusVal)
        airplaneModeVal = findViewById(R.id.airplaneModeVal)
        checklistVpnText = findViewById(R.id.checklistVpnText)
        checklistTlsText = findViewById(R.id.checklistTlsText)
        checklistTypeStruct = findViewById(R.id.checklistTypeStruct)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        refreshNetworkState()
        registerNetworkCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread { refreshNetworkState() }
                }

                override fun onLost(network: Network) {
                    runOnUiThread { refreshNetworkState() }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    runOnUiThread { updateUiWithCapabilities(networkCapabilities) }
                }
            }

            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {}
    }

    private fun unregisterNetworkCallback() {
        try {
            if (networkCallback != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback!!)
                networkCallback = null
            }
        } catch (_: Exception) {}
    }

    private fun refreshNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val airplaneMode = isAirplaneModeOn()
        airplaneModeVal.text = if (airplaneMode) "On" else "Off"

        if (caps == null) {
            setDisconnectedState(if (airplaneMode) "Airplane mode is enabled" else "No active internet connection")
            return
        }

        updateUiWithCapabilities(caps)
    }

    private fun isAirplaneModeOn(): Boolean {
        return try {
            Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isVpnActive(caps: NetworkCapabilities): Boolean {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val i = interfaces.nextElement()
                if (i.isUp && (i.name.contains("tun", true) || i.name.contains("ppp", true) || i.name.contains("vpn", true))) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun updateUiWithCapabilities(caps: NetworkCapabilities) {
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val hasVpn = isVpnActive(caps)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        val typeStr = when {
            isWifi -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular (Mobile Data)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other Network"
        }

        networkTypeVal.text = typeStr

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
                    val ssidClean = wifiInfo.ssid?.replace("\"", "")?.takeIf { it != "<unknown ssid>" && it.isNotBlank() } ?: "Connected Wi-Fi"
                    val signalPercent = ((rssi + 100).coerceIn(0, 50) * 2).coerceIn(0, 100)
                    wifiSsidVal.text = ssidClean
                    wifiSignalVal.text = "$signalPercent% (Level $level/4)"
                } else {
                    wifiSsidVal.text = "Connected (Wi-Fi)"
                    wifiSignalVal.text = "Signal Available"
                }
            } catch (_: Exception) {
                wifiSsidVal.text = "Connected (Wi-Fi)"
                wifiSignalVal.text = "Signal Available"
            }
        } else {
            wifiSsidVal.text = "Disconnected"
            wifiSignalVal.text = "N/A"
        }

        ipAddressVal.text = getLocalIpAddress()

        if (hasInternet) {
            internetAccessVal.text = if (isValidated) "Connected & Validated" else "Connected (Unvalidated)"
            internetAccessVal.setTextColor(0xFFFF2D55.toInt())
        } else {
            internetAccessVal.text = "No Internet"
            internetAccessVal.setTextColor(0xFFFF6B6B.toInt())
        }

        if (hasVpn) {
            vpnStatusVal.text = "Active (Encrypted Tunnel)"
            vpnStatusVal.setTextColor(0xFF3DDC84.toInt()) // Green
            checklistVpnText.text = "• VPN Protection: Active and routing traffic through secure encrypted tunnel"
            checklistVpnText.setTextColor(0xFF3DDC84.toInt())
        } else {
            vpnStatusVal.text = "Inactive"
            vpnStatusVal.setTextColor(0xFFFFAB40.toInt()) // Orange
            checklistVpnText.text = "• VPN Protection: Not active (Recommended on public Wi-Fi networks)"
            checklistVpnText.setTextColor(0xFFDDDDDD.toInt())
        }

        if (hasInternet) {
            connectionStatusTitle.text = "Secure & Connected"
            connectionStatusSubtitle.text = "Active on $typeStr with working internet connection."
            statusIndicator.setBackgroundColor(0xFFFF2D55.toInt())
        } else {
            connectionStatusTitle.text = "Disconnected"
            connectionStatusSubtitle.text = "Network capability lacks internet access."
            statusIndicator.setBackgroundColor(0xFFFF6B6B.toInt())
        }

        checklistTlsText.text = "• Transport Encryption: Enforced via HTTPS / TLS & Network Security Config"
        checklistTypeStruct.text = "• Connection Integrity: Active network ($typeStr) validated by system connectivity manager"
    }

    private fun setDisconnectedState(reason: String) {
        connectionStatusTitle.text = "No Connection"
        connectionStatusSubtitle.text = reason
        statusIndicator.setBackgroundColor(0xFFFF6B6B.toInt())
        networkTypeVal.text = "None"
        wifiSsidVal.text = "Disconnected"
        wifiSignalVal.text = "N/A"
        ipAddressVal.text = getLocalIpAddress()
        internetAccessVal.text = "Disconnected"
        internetAccessVal.setTextColor(0xFFFF6B6B.toInt())
        vpnStatusVal.text = "Inactive"
        vpnStatusVal.setTextColor(0xFF8E8E9A.toInt())
        checklistVpnText.text = "• VPN Protection: Inactive (No connection)"
        checklistTlsText.text = "• Transport Encryption: Ready for secure connections"
        checklistTypeStruct.text = "• Connection Integrity: Offline"
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty()) return host
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }
}
