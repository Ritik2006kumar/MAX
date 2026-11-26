package dev.voxpilot.assistant.security

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class BatteryDrainActivity : AppCompatActivity() {

    data class DrainItem(val appName: String, val packageName: String, val status: String, val isRunning: Boolean)

    private lateinit var drainListView: ListView
    private val drainList = mutableListOf<DrainItem>()
    private lateinit var adapter: DrainAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery_drain)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        drainListView = findViewById(R.id.drainListView)
        adapter = DrainAdapter(this, drainList)
        drainListView.adapter = adapter

        loadRunningApps()
    }

    private fun loadRunningApps() {
        drainList.clear()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = am.runningAppProcesses ?: emptyList()
        val runningPkgs = runningProcesses.flatMap { it.pkgList.toList() }.toSet()

        val pm = packageManager
        val installed = pm.getInstalledApplications(0)

        for (app in installed) {
            val isRunning = runningPkgs.contains(app.packageName)
            if (isRunning || app.packageName == packageName) {
                val label = try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName }
                drainList.add(
                    DrainItem(
                        appName = label,
                        packageName = app.packageName,
                        status = if (isRunning) "Active Background Process" else "Idle",
                        isRunning = isRunning
                    )
                )
            }
        }

        drainList.sortByDescending { it.isRunning }
        adapter.notifyDataSetChanged()
    }

    private class DrainAdapter(context: Context, private val items: List<DrainItem>) :
        ArrayAdapter<DrainItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_permission_app, parent, false)
            val item = items[position]

            val nameView = view.findViewById<TextView>(R.id.appName)
            val packageView = view.findViewById<TextView>(R.id.appPackage)
            val summaryView = view.findViewById<TextView>(R.id.appSummary)
            val riskBadge = view.findViewById<TextView>(R.id.riskBadge)
            val iconView = view.findViewById<ImageView>(R.id.appIcon)

            nameView.text = item.appName
            packageView.text = item.packageName
            summaryView.text = item.status
            try {
                val icon = context.packageManager.getApplicationIcon(item.packageName)
                iconView.setImageDrawable(icon)
            } catch (_: Exception) {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            riskBadge.text = if (item.isRunning) "RUNNING" else "IDLE"
            riskBadge.setTextColor(if (item.isRunning) 0xFFFFAB40.toInt() else 0xFF8E8E9A.toInt())
            riskBadge.setBackgroundColor(if (item.isRunning) 0x33FFAB40 else 0x338E8E9A)

            return view
        }
    }
}
