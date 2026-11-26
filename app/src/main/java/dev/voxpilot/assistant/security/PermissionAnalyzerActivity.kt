package dev.voxpilot.assistant.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import kotlinx.coroutines.*

class PermissionAnalyzerActivity : AppCompatActivity() {

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class AppPermissionItem(
        val name: String,
        val packageName: String,
        val icon: Drawable,
        val versionName: String,
        val riskLevel: RiskLevel,
        val sensitiveCount: Int,
        val summary: String,
        val permissions: List<String>,
        val grantedPermissions: List<String>
    )

    private lateinit var searchInput: EditText
    private lateinit var riskFilterSpinner: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var listView: ListView

    private val allApps = mutableListOf<AppPermissionItem>()
    private val filteredApps = mutableListOf<AppPermissionItem>()
    private lateinit var adapter: AppsAdapter

    private var currentRiskFilter = "All"
    private var searchQuery = ""

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_analyzer)

        searchInput = findViewById(R.id.searchInput)
        riskFilterSpinner = findViewById(R.id.riskFilterSpinner)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        listView = findViewById(R.id.listView)

        findViewById<View>(R.id.backBtn).setOnClickListener { finish() }

        setupSpinner()
        setupSearch()

        adapter = AppsAdapter(this, filteredApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val app = filteredApps[position]
            val intent = Intent(this, PermissionDetailActivity::class.java).apply {
                putExtra("package_name", app.packageName)
            }
            startActivity(intent)
        }

        loadAppsAsync()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun setupSpinner() {
        val filters = arrayOf("All Risk Levels", "High Risk", "Medium Risk", "Low Risk")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        riskFilterSpinner.adapter = spinnerAdapter
        riskFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentRiskFilter = when (position) {
                    1 -> "High"
                    2 -> "Medium"
                    3 -> "Low"
                    else -> "All"
                }
                filterApps()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                filterApps()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAppsAsync() {
        progressBar.visibility = View.VISIBLE
        listView.visibility = View.GONE
        emptyView.visibility = View.GONE

        scope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
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

            val parsedList = mutableListOf<AppPermissionItem>()

            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg.packageName }
                val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { getDrawable(android.R.drawable.sym_def_app_icon)!! }
                val versionName = pkg.versionName ?: "1.0"

                val requestedPerms = pkg.requestedPermissions?.toList() ?: emptyList()
                val requestedFlags = pkg.requestedPermissionsFlags ?: intArrayOf()

                val grantedPerms = mutableListOf<String>()
                for (i in requestedPerms.indices) {
                    if (i < requestedFlags.size && (requestedFlags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        grantedPerms.add(requestedPerms[i])
                    }
                }

                val sensitiveGrantedCount = grantedPerms.count { it in sensitivePermissions }

                val risk = when {
                    sensitiveGrantedCount >= 3 -> RiskLevel.HIGH
                    sensitiveGrantedCount >= 1 -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }

                val summary = if (sensitiveGrantedCount > 0) {
                    "$sensitiveGrantedCount sensitive permissions granted"
                } else {
                    "Minimal permissions required"
                }

                parsedList.add(
                    AppPermissionItem(
                        name = label,
                        packageName = pkg.packageName,
                        icon = icon,
                        versionName = versionName,
                        riskLevel = risk,
                        sensitiveCount = sensitiveGrantedCount,
                        summary = summary,
                        permissions = requestedPerms,
                        grantedPermissions = grantedPerms
                    )
                )
            }

            parsedList.sortBy { it.name.lowercase() }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(parsedList)
                filterApps()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun filterApps() {
        filteredApps.clear()
        val q = searchQuery.lowercase()

        for (app in allApps) {
            val matchesSearch = q.isBlank() || app.name.lowercase().contains(q) || app.packageName.lowercase().contains(q)
            val matchesRisk = when (currentRiskFilter) {
                "High" -> app.riskLevel == RiskLevel.HIGH
                "Medium" -> app.riskLevel == RiskLevel.MEDIUM
                "Low" -> app.riskLevel == RiskLevel.LOW
                else -> true
            }

            if (matchesSearch && matchesRisk) {
                filteredApps.add(app)
            }
        }

        if (filteredApps.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }
        adapter.notifyDataSetChanged()
    }

    private class AppsAdapter(context: Context, private val items: List<AppPermissionItem>) :
        ArrayAdapter<AppPermissionItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_permission_app, parent, false)
            val app = items[position]

            val iconView = view.findViewById<ImageView>(R.id.appIcon)
            val nameView = view.findViewById<TextView>(R.id.appName)
            val packageView = view.findViewById<TextView>(R.id.appPackage)
            val summaryView = view.findViewById<TextView>(R.id.appSummary)
            val riskBadge = view.findViewById<TextView>(R.id.riskBadge)

            iconView.setImageDrawable(app.icon)
            nameView.text = app.name
            packageView.text = app.packageName
            summaryView.text = app.summary

            when (app.riskLevel) {
                RiskLevel.HIGH -> {
                    riskBadge.text = "HIGH"
                    riskBadge.setTextColor(0xFFFF6B6B.toInt())
                    riskBadge.setBackgroundColor(0x33FF6B6B)
                }
                RiskLevel.MEDIUM -> {
                    riskBadge.text = "MED"
                    riskBadge.setTextColor(0xFFFFAB40.toInt())
                    riskBadge.setBackgroundColor(0x33FFAB40)
                }
                RiskLevel.LOW -> {
                    riskBadge.text = "LOW"
                    riskBadge.setTextColor(0xFF3DDC84.toInt())
                    riskBadge.setBackgroundColor(0x333DDC84)
                }
            }

            return view
        }
    }
}
