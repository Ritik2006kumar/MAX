package dev.voxpilot.assistant.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    val REQUIRED_PERMISSIONS: Array<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.WRITE_CONTACTS)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.ANSWER_PHONE_CALLS)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasMicPermission(context: Context) = hasPermission(context, Manifest.permission.RECORD_AUDIO)

    fun getMissingPermissions(context: Context): List<String> =
        REQUIRED_PERMISSIONS.filter { !hasPermission(context, it) }

    fun requestMissing(activity: Activity, requestCode: Int) {
        val missing = getMissingPermissions(activity)
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        }
    }
}
