package dev.voxpilot.assistant.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.utils.Constants
import kotlinx.coroutines.*

object ContinuousSecurityScanner {
    private const val TAG = "ContinuousSecurity"
    private var job: Job? = null

    fun start(context: Context) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    scanAndCheckThreats(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Continuous scan error: ${e.message}")
                }
                delay(30 * 60 * 1000L)
            }
        }
        Log.d(TAG, "Continuous 24/7 Background Security Scanner started ✅")
    }

    private fun scanAndCheckThreats(context: Context) {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val sensitivePerms = setOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_SMS
        )

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue

            val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg.packageName }
            val perms = pkg.requestedPermissions ?: continue
            val flags = pkg.requestedPermissionsFlags ?: continue

            var dangerousCount = 0
            for (i in perms.indices) {
                if (i < flags.size && (flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    if (perms[i] in sensitivePerms) {
                        dangerousCount++
                    }
                }
            }

            if (dangerousCount >= 3 && pkg.packageName != context.packageName) {
                showSecurityAlert(context, appName)
            }
        }
    }

    private fun showSecurityAlert(context: Context, appName: String) {
        try {
            val builder = NotificationCompat.Builder(context, Constants.NOTIF_CHANNEL_VOICE)
                .setSmallIcon(R.mipmap.img)
                .setContentTitle("🛡️ Max 24/7 Security Guard")
                .setContentText("Privacy Alert: '$appName' holds background Camera, Mic, or Location access!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify((appName.hashCode() and 0x7FFFFFFF), builder.build())
        } catch (_: Exception) {}
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
