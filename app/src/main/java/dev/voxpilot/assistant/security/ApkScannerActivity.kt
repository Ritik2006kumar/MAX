package dev.voxpilot.assistant.security

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import kotlinx.coroutines.*

class ApkScannerActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var apkListView: ListView

    private val apkList = mutableListOf<ApkScanner.ApkItem>()
    private lateinit var adapter: ApkAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apk_scanner)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        apkListView = findViewById(R.id.apkListView)

        adapter = ApkAdapter(this, apkList)
        apkListView.adapter = adapter

        apkListView.setOnItemClickListener { _, _, position, _ ->
            val item = apkList[position]
            val details = item.permissions.joinToString("\n") { "• $it" }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(item.name)
                .setMessage("Package / Path:\n${item.path}\n\nRisk Level: ${item.riskLevel}\nSummary: ${item.summary}\n\nRequested Permissions (${item.permissions.size}):\n$details")
                .setPositiveButton("Close", null)
                .show()
        }

        scanApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun scanApps() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        apkListView.visibility = View.GONE

        scope.launch(Dispatchers.IO) {
            val results = ApkScanner.scanDeviceAppsAndFiles(this@ApkScannerActivity)

            withContext(Dispatchers.Main) {
                apkList.clear()
                apkList.addAll(results)
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                if (apkList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    apkListView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    apkListView.visibility = View.VISIBLE
                }
            }
        }
    }

    private class ApkAdapter(context: Context, private val items: List<ApkScanner.ApkItem>) :
        ArrayAdapter<ApkScanner.ApkItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_permission_app, parent, false)
            val item = items[position]

            val nameView = view.findViewById<TextView>(R.id.appName)
            val packageView = view.findViewById<TextView>(R.id.appPackage)
            val summaryView = view.findViewById<TextView>(R.id.appSummary)
            val riskBadge = view.findViewById<TextView>(R.id.riskBadge)
            val iconView = view.findViewById<ImageView>(R.id.appIcon)

            nameView.text = item.name
            packageView.text = item.packageName
            summaryView.text = item.summary

            try {
                val icon = context.packageManager.getApplicationIcon(item.packageName)
                iconView.setImageDrawable(icon)
            } catch (_: Exception) {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            when (item.riskLevel) {
                "HIGH" -> {
                    riskBadge.text = "HIGH"
                    riskBadge.setTextColor(0xFFFF6B6B.toInt())
                    riskBadge.setBackgroundColor(0x33FF6B6B)
                }
                "MEDIUM" -> {
                    riskBadge.text = "MED"
                    riskBadge.setTextColor(0xFFFFAB40.toInt())
                    riskBadge.setBackgroundColor(0x33FFAB40)
                }
                else -> {
                    riskBadge.text = "LOW"
                    riskBadge.setTextColor(0xFF3DDC84.toInt())
                    riskBadge.setBackgroundColor(0x333DDC84)
                }
            }

            return view
        }
    }
}
