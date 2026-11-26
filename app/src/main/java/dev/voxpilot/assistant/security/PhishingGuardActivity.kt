package dev.voxpilot.assistant.security

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class PhishingGuardActivity : AppCompatActivity() {

    private lateinit var guardListView: ListView
    private lateinit var emptyView: TextView
    private lateinit var clearAlertsBtn: TextView

    private val alertList = mutableListOf<PhishingGuardManager.SmsAlert>()
    private lateinit var adapter: GuardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phishing_guard)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        guardListView = findViewById(R.id.guardListView)
        emptyView = findViewById(R.id.emptyView)
        clearAlertsBtn = findViewById(R.id.clearAlertsBtn)

        adapter = GuardAdapter(this, alertList)
        guardListView.adapter = adapter

        clearAlertsBtn.setOnClickListener {
            PhishingGuardManager.clearAlerts(this)
            loadAlerts()
            Toast.makeText(this, "Alert logs cleared", Toast.LENGTH_SHORT).show()
        }

        loadAlerts()
    }

    private fun loadAlerts() {
        alertList.clear()
        alertList.addAll(PhishingGuardManager.getAlerts(this))
        adapter.notifyDataSetChanged()

        if (alertList.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            guardListView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            guardListView.visibility = View.VISIBLE
        }
    }

    private class GuardAdapter(context: Context, private val items: List<PhishingGuardManager.SmsAlert>) :
        ArrayAdapter<PhishingGuardManager.SmsAlert>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_permission_app, parent, false)
            val alert = items[position]

            val nameView = view.findViewById<TextView>(R.id.appName)
            val packageView = view.findViewById<TextView>(R.id.appPackage)
            val summaryView = view.findViewById<TextView>(R.id.appSummary)
            val riskBadge = view.findViewById<TextView>(R.id.riskBadge)
            val iconView = view.findViewById<ImageView>(R.id.appIcon)

            nameView.text = "From: ${alert.sender}"
            packageView.text = alert.body
            summaryView.text = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(alert.timestamp))
            iconView.setImageResource(android.R.drawable.ic_dialog_alert)

            riskBadge.text = "SCAM"
            riskBadge.setTextColor(0xFFFF6B6B.toInt())
            riskBadge.setBackgroundColor(0x33FF6B6B)

            return view
        }
    }
}
