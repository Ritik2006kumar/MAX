package dev.voxpilot.assistant.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File
import java.util.Locale

object ApkScanner {

    data class ApkItem(
        val name: String,
        val path: String,
        val packageName: String,
        val riskLevel: String, // HIGH, MEDIUM, LOW
        val summary: String,
        val permissions: List<String>
    )

    fun scanDeviceAppsAndFiles(context: Context): List<ApkItem> {
        val list = mutableListOf<ApkItem>()
        val pm = context.packageManager

        // 1. Scan installed apps
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val dangerousPerms = setOf(
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE
        )

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue // Focus on 3rd-party user apps

            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg.packageName }
            val perms = pkg.requestedPermissions?.toList() ?: emptyList()
            val flags = pkg.requestedPermissionsFlags ?: intArrayOf()

            var riskyCount = 0
            for (i in perms.indices) {
                if (i < flags.size && (flags[i] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                    if (perms[i] in dangerousPerms) {
                        riskyCount++
                    }
                }
            }

            val risk = when {
                riskyCount >= 3 -> "HIGH"
                riskyCount >= 1 -> "MEDIUM"
                else -> "LOW"
            }

            list.add(
                ApkItem(
                    name = label,
                    path = appInfo.sourceDir ?: "Installed App",
                    packageName = pkg.packageName,
                    riskLevel = risk,
                    summary = if (riskyCount > 0) "$riskyCount sensitive permissions held" else "Clean profile",
                    permissions = perms
                )
            )
        }

        // 2. Scan Downloads folder for external APK files
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                val apkFiles = downloadsDir.listFiles { _, name -> name.lowercase(Locale.ROOT).endsWith(".apk") }
                if (apkFiles != null) {
                    for (file in apkFiles) {
                        try {
                            val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
                            if (pkgInfo != null) {
                                val label = pkgInfo.applicationInfo?.let {
                                    it.sourceDir = file.absolutePath
                                    it.publicSourceDir = file.absolutePath
                                    try { pm.getApplicationLabel(it).toString() } catch (_: Exception) { file.name }
                                } ?: file.name

                                val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()
                                val riskyCount = perms.count { it in dangerousPerms }

                                val risk = when {
                                    riskyCount >= 3 -> "HIGH"
                                    riskyCount >= 1 -> "MEDIUM"
                                    else -> "LOW"
                                }

                                list.add(
                                    ApkItem(
                                        name = "[APK File] $label",
                                        path = file.absolutePath,
                                        packageName = pkgInfo.packageName ?: file.name,
                                        riskLevel = risk,
                                        summary = "Downloaded APK • $riskyCount risky permissions",
                                        permissions = perms
                                    )
                                )
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        list.sortByDescending { it.riskLevel == "HIGH" }
        return list
    }
}
