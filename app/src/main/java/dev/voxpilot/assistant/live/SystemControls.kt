package dev.voxpilot.assistant.live

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.voxpilot.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phone settings: Wi-Fi, Bluetooth, mobile data, torch, volume, brightness, airplane, location...
 *
 * Modern Android (10+) forbids apps from flipping Wi-Fi / mobile data / Bluetooth themselves, so
 * those go through the real system panel and VoxPilot's accessibility service taps the switch - the
 * same thing the user would do. The result always says whether the state was verified.
 */
object SystemControls {
    private const val TAG = "SystemControls"

    private fun res(result: String, vararg extra: Pair<String, Any?>) =
        JSONObject().put("result", result).also { o -> extra.forEach { (k, v) -> o.put(k, v) } }

    private fun a11y() = AccessibilityHelperService.instance

    private fun has(context: Context, p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun parseOnOff(value: String): Boolean? = when (value.trim().lowercase()) {
        "on", "true", "enable", "enabled", "start", "1", "yes", "chalu" -> true
        "off", "false", "disable", "disabled", "stop", "0", "no", "band" -> false
        else -> null
    }

    suspend fun apply(context: Context, setting: String, value: String): JSONObject {
        val s = setting.lowercase().trim().replace(' ', '_').replace('-', '_')
        return when (s) {
            "wifi", "wi_fi" -> wifi(context, parseOnOff(value) ?: return res("invalid_value"))
            "bluetooth", "bt" -> bluetooth(context, parseOnOff(value) ?: return res("invalid_value"))
            "mobile_data", "data", "cellular", "internet" -> mobileData(context, parseOnOff(value) ?: return res("invalid_value"))
            "flashlight", "torch", "flash" -> torch(context, parseOnOff(value) ?: return res("invalid_value"))
            "airplane", "airplane_mode", "flight_mode" -> airplane(context, parseOnOff(value) ?: return res("invalid_value"))
            "location", "gps" -> location(context, parseOnOff(value) ?: return res("invalid_value"))
            "hotspot", "tethering" -> hotspot(context, parseOnOff(value) ?: return res("invalid_value"))
            "nfc" -> nfc(context, parseOnOff(value) ?: return res("invalid_value"))
            "battery_saver", "power_saving" -> batterySaver(context, parseOnOff(value) ?: return res("invalid_value"))
            "dnd", "do_not_disturb", "silent_mode" -> dnd(context, parseOnOff(value) ?: return res("invalid_value"))
            "auto_rotate", "rotation" -> autoRotate(context, parseOnOff(value) ?: return res("invalid_value"))
            "volume" -> volume(context, value)
            "brightness" -> brightness(context, value)
            "ringer" -> ringer(context, value)
            "dark_mode", "dark_theme", "theme", "light_mode" -> {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                DeviceActions.launch(context, intent)
                res("opened", "setting" to "display_theme_settings", "note" to "Opened Display & Theme settings to adjust dark/light theme.")
            }
            else -> res("unknown_setting", "setting" to setting)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Generic "open a system page and flip its switch" helper
    // ---------------------------------------------------------------------------------------

    private suspend fun toggleViaUi(
        context: Context,
        intents: List<Intent>,
        hints: List<String>,
        on: Boolean,
        current: (() -> Boolean?)?,
        closeAfter: Boolean = true,
    ): JSONObject {
        if (current?.invoke() == on) return res("already_in_state", "state" to if (on) "on" else "off")

        val svc = a11y()
        val launched = intents.firstOrNull { DeviceActions.launch(context, Intent(it)) }
            ?: return res("failed", "detail" to "settings page not available")
        if (svc == null) {
            return res("opened_settings_user_must_toggle", "note" to "Accessibility is off, so the user must flip the switch. Tell them which one.")
        }

        val before = svc.windowStamp
        val deadline = System.currentTimeMillis() + 2500
        while (svc.windowStamp == before && System.currentTimeMillis() < deadline) delay(80)

        var step = "switch_not_found"
        repeat(3) { attempt ->
            if (step == "switch_not_found") {
                step = svc.setSwitch(on, hints)
                if (step == "switch_not_found") delay(500L * (attempt + 1))
            }
        }
        delay(700)
        val state = current?.invoke()
        if (closeAfter) svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Log.d(TAG, "toggleViaUi launched=${launched.action} step=$step state=$state")

        return when {
            step == "switch_not_found" -> res("opened_settings_user_must_toggle", "note" to "Could not find the switch automatically.")
            state == on -> res("done", "state" to if (on) "on" else "off")
            state == null -> res("toggle_attempted_unverified")
            else -> res("toggle_attempted_but_state_unchanged")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Connectivity
    // ---------------------------------------------------------------------------------------

    private fun wifiState(context: Context): Boolean? =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled

    @Suppress("DEPRECATION")
    private suspend fun wifi(context: Context, on: Boolean): JSONObject {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (wm.isWifiEnabled == on) return res("already_in_state")
            return if (wm.setWifiEnabled(on)) res("done") else res("failed")
        }
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(Intent(Settings.Panel.ACTION_WIFI))
            add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            add(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        return toggleViaUi(context, intents, listOf("wi-fi", "wifi", "use wi"), on, { wifiState(context) })
    }

    private fun btAdapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Suppress("DEPRECATION", "MissingPermission")
    private suspend fun bluetooth(context: Context, on: Boolean): JSONObject {
        val adapter = btAdapter(context) ?: return res("not_supported")
        if (adapter.isEnabled == on) return res("already_in_state")

        // Direct API still works below Android 13 when the app holds the Bluetooth permission.
        val canDirect = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || has(context, Manifest.permission.BLUETOOTH_CONNECT))
        if (canDirect) {
            val ok = try { if (on) adapter.enable() else adapter.disable() } catch (_: SecurityException) { false }
            if (ok) {
                repeat(10) { if (adapter.isEnabled != on) delay(200) }
                return if (adapter.isEnabled == on) res("done") else res("toggle_attempted_unverified")
            }
        }

        if (on && a11y() != null) {
            // The system's own "allow VoxPilot to turn on Bluetooth?" dialog, confirmed through accessibility.
            if (DeviceActions.launch(context, Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))) {
                val svc = a11y()!!
                repeat(8) {
                    delay(300)
                    if (svc.clickByText("Allow", exactOnly = true) || svc.clickByText("Yes", exactOnly = true) || svc.clickByText("Turn on", exactOnly = true)) {
                        repeat(8) { if (!adapter.isEnabled) delay(250) }
                        return if (adapter.isEnabled) res("done") else res("toggle_attempted_unverified")
                    }
                }
            }
        }
        return toggleViaUi(context, listOf(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)), listOf("bluetooth", "use bluetooth"), on, { adapter.isEnabled })
    }

    private fun dataState(context: Context): Boolean? {
        if (!has(context, Manifest.permission.READ_PHONE_STATE)) return null
        return try {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).isDataEnabled
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun mobileData(context: Context, on: Boolean): JSONObject {
        val intents = buildList {
            add(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            add(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))
            add(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
        return toggleViaUi(context, intents, listOf("mobile data", "cellular", "sim", "data"), on, { dataState(context) })
    }

    private suspend fun airplane(context: Context, on: Boolean): JSONObject = toggleViaUi(
        context, listOf(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)), listOf("airplane", "flight"), on,
        { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 },
    )

    private suspend fun location(context: Context, on: Boolean): JSONObject = toggleViaUi(
        context, listOf(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)), listOf("use location", "location"), on,
        {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled else null
        },
    )

    private suspend fun hotspot(context: Context, on: Boolean): JSONObject {
        val tether = Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings"))
        return toggleViaUi(context, listOf(tether, Intent(Settings.ACTION_WIRELESS_SETTINGS)), listOf("hotspot", "tethering"), on, null)
    }

    private suspend fun nfc(context: Context, on: Boolean): JSONObject {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Intent(Settings.Panel.ACTION_NFC))
            add(Intent(Settings.ACTION_NFC_SETTINGS))
        }
        val adapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
        return toggleViaUi(context, intents, listOf("nfc"), on, adapter?.let { { it.isEnabled } })
    }

    private suspend fun batterySaver(context: Context, on: Boolean): JSONObject {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return toggleViaUi(context, listOf(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)), listOf("battery saver", "use battery saver"), on, { pm.isPowerSaveMode })
    }

    // ---------------------------------------------------------------------------------------
    // Direct system settings
    // ---------------------------------------------------------------------------------------

    private fun torch(context: Context, on: Boolean): JSONObject {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return res("no_flashlight")
        cm.setTorchMode(id, on)
        return res("done", "state" to if (on) "on" else "off")
    }

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun volume(context: Context, value: String): JSONObject {
        val am = audio(context)
        // During a call the user means the call volume.
        val stream = if (am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream)
        val v = value.trim().lowercase()
        when {
            v == "up" || v == "increase" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            v == "down" || v == "decrease" -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            v == "max" || v == "full" -> am.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
            v == "min" || v == "mute" -> am.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
            else -> {
                val pct = v.trim('%', ' ').toIntOrNull() ?: return res("invalid_value")
                am.setStreamVolume(stream, (pct.coerceIn(0, 100) * max / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
            }
        }
        return res("done", "volume_percent" to (am.getStreamVolume(stream) * 100 / max))
    }

    private fun brightness(context: Context, value: String): JSONObject {
        if (!Settings.System.canWrite(context)) {
            DeviceActions.launch(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return res("permission_needed", "note" to "Opened the 'Modify system settings' page; the user must allow VoxPilot there once.")
        }
        val cr = context.contentResolver
        val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
        val v = value.trim().lowercase()
        val target = when {
            v == "up" || v == "increase" -> current + 25
            v == "down" || v == "decrease" -> current - 25
            v == "max" || v == "full" -> 100
            v == "min" -> 1
            else -> v.trim('%', ' ').toIntOrNull() ?: return res("invalid_value")
        }.coerceIn(1, 100)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target * 255 / 100)
        return res("done", "brightness_percent" to target)
    }

    private fun ringer(context: Context, value: String): JSONObject {
        val am = audio(context)
        return try {
            am.ringerMode = when (value.trim().lowercase()) {
                "normal", "ring", "loud" -> AudioManager.RINGER_MODE_NORMAL
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                "silent", "mute" -> AudioManager.RINGER_MODE_SILENT
                else -> return res("invalid_value")
            }
            res("done", "mode" to value)
        } catch (e: SecurityException) {
            DeviceActions.launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            res("permission_needed", "note" to "Silent mode needs 'Do Not Disturb access' for VoxPilot; opened that page.")
        }
    }

    private fun dnd(context: Context, on: Boolean): JSONObject {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            DeviceActions.launch(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return res("permission_needed", "note" to "Opened 'Do Not Disturb access'; the user must allow VoxPilot there once.")
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return res("done", "state" to if (on) "on" else "off")
    }

    private fun autoRotate(context: Context, on: Boolean): JSONObject {
        if (!Settings.System.canWrite(context)) {
            DeviceActions.launch(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
            return res("permission_needed")
        }
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
        return res("done", "state" to if (on) "on" else "off")
    }

    // ---------------------------------------------------------------------------------------
    // Status / pages
    // ---------------------------------------------------------------------------------------

    fun status(context: Context): JSONObject {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plug = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val am = audio(context)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return res(
            "ok",
            "battery_percent" to if (level >= 0) level * 100 / scale else null,
            "charging" to (plug != 0),
            "wifi" to wifiState(context),
            "bluetooth" to btAdapter(context)?.isEnabled,
            "mobile_data" to dataState(context),
            "media_volume_percent" to (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max),
            "time" to SimpleDateFormat("EEEE, d MMM yyyy, hh:mm a", Locale.getDefault()).format(Date()),
        )
    }

    fun openPage(context: Context, page: String): JSONObject {
        val action = when (page.lowercase().trim()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "notifications" -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "date", "time" -> Settings.ACTION_DATE_SETTINGS
            "language" -> Settings.ACTION_LOCALE_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "network", "mobile", "data" -> Settings.ACTION_WIRELESS_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "main", "settings", "" -> Settings.ACTION_SETTINGS
            else -> return res("unknown_page", "page" to page)
        }
        val intent = Intent(action)
        if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        return if (DeviceActions.launch(context, intent)) res("opened", "page" to page) else res("failed")
    }
}
