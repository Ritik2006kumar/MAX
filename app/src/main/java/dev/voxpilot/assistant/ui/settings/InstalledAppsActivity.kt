package dev.voxpilot.assistant.ui.settings

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

class InstalledAppsActivity : AppCompatActivity() {

    data class AppItem(val name: String, val packageName: String, val icon: Drawable)

    private lateinit var listView: ListView
    private lateinit var searchInput: EditText
    private var allApps = mutableListOf<AppItem>()
    private var filteredApps = mutableListOf<AppItem>()
    private lateinit var adapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_installed_apps)

        listView = findViewById(R.id.appsListView)
        searchInput = findViewById(R.id.appSearchInput)

        loadInstalledApps()

        adapter = AppsAdapter(this, filteredApps)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val app = filteredApps[position]
            val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Could not open ${app.name}", Toast.LENGTH_SHORT).show()
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }

        allApps.clear()
        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName) ?: continue
            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg.packageName }
            val icon = try { pm.getApplicationIcon(appInfo) } catch (_: Exception) { getDrawable(android.R.drawable.sym_def_app_icon)!! }

            allApps.add(AppItem(label, pkg.packageName, icon))
        }

        allApps.sortBy { it.name.lowercase() }
        filteredApps.clear()
        filteredApps.addAll(allApps)
    }

    private fun filter(query: String) {
        filteredApps.clear()
        if (query.isBlank()) {
            filteredApps.addAll(allApps)
        } else {
            val q = query.lowercase()
            for (app in allApps) {
                if (app.name.lowercase().contains(q) || app.packageName.lowercase().contains(q)) {
                    filteredApps.add(app)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    private class AppsAdapter(context: Context, private val items: List<AppItem>) :
        ArrayAdapter<AppItem>(context, 0, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app_row, parent, false)
            val app = items[position]

            val iconView = view.findViewById<ImageView>(R.id.appIcon)
            val textView = view.findViewById<TextView>(R.id.appName)

            val customIconFile = java.io.File(context.filesDir, "app_icon_${app.packageName}.png")
            if (customIconFile.exists()) {
                try {
                    iconView.setImageURI(android.net.Uri.fromFile(customIconFile))
                } catch (_: Exception) {
                    iconView.setImageDrawable(app.icon)
                }
            } else {
                iconView.setImageDrawable(app.icon)
            }
            textView.text = app.name

            val prefs = context.getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
            val theme = prefs.getString("app_icon_theme", "default")
            when (theme) {
                "midnight" -> iconView.setColorFilter(0xFF2C2C2E.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY)
                "neon" -> iconView.setColorFilter(0xFFFF2D55.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY)
                "glass" -> iconView.setColorFilter(0xFF00FFFF.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY)
                "gold" -> iconView.setColorFilter(0xFFFFD700.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY)
                "hacker" -> iconView.setColorFilter(0xFF00FF00.toInt(), android.graphics.PorterDuff.Mode.MULTIPLY)
                else -> iconView.clearColorFilter()
            }

            return view
        }
    }
}
