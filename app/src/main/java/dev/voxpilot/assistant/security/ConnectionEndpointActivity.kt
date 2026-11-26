package dev.voxpilot.assistant.security

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import java.net.NetworkInterface

class ConnectionEndpointActivity : AppCompatActivity() {

    data class EndpointItem(val interfaceName: String, val ipAddress: String, val isUp: Boolean, val type: String)

    private lateinit var endpointListView: ListView
    private val endpointList = mutableListOf<EndpointItem>()
    private lateinit var adapter: EndpointAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_endpoints)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        endpointListView = findViewById(R.id.endpointListView)
        adapter = EndpointAdapter(this, endpointList)
        endpointListView.adapter = adapter

        loadEndpoints()
    }

    private fun loadEndpoints() {
        endpointList.clear()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val name = intf.name ?: "unknown"
                val isUp = intf.isUp
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val host = addr.hostAddress ?: ""
                    val type = if (addr is java.net.Inet6Address) "IPv6" else "IPv4"
                    endpointList.add(EndpointItem(name, host, isUp, type))
                }
            }
        } catch (_: Exception) {}

        endpointList.sortByDescending { it.isUp }
        adapter.notifyDataSetChanged()
    }

    private class EndpointAdapter(context: Context, private val items: List<EndpointItem>) :
        ArrayAdapter<EndpointItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_permission_app, parent, false)
            val item = items[position]

            val nameView = view.findViewById<TextView>(R.id.appName)
            val packageView = view.findViewById<TextView>(R.id.appPackage)
            val summaryView = view.findViewById<TextView>(R.id.appSummary)
            val riskBadge = view.findViewById<TextView>(R.id.riskBadge)
            val iconView = view.findViewById<ImageView>(R.id.appIcon)

            nameView.text = "Interface: ${item.interfaceName} (${item.type})"
            packageView.text = "IP: ${item.ipAddress}"
            summaryView.text = if (item.isUp) "Status: Active (UP)" else "Status: Inactive"
            iconView.setImageResource(android.R.drawable.ic_menu_manage)

            riskBadge.text = if (item.isUp) "ACTIVE" else "DOWN"
            riskBadge.setTextColor(if (item.isUp) 0xFF3DDC84.toInt() else 0xFF8E8E9A.toInt())
            riskBadge.setBackgroundColor(if (item.isUp) 0x333DDC84 else 0x338E8E9A)

            return view
        }
    }
}
