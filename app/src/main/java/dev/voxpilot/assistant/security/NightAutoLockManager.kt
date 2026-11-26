package dev.voxpilot.assistant.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import java.util.Calendar

object NightAutoLockManager {
    private var job: Job? = null
    private const val TAG = "NightAutoLock"
    private var lastTouchTime = SystemClock.uptimeMillis()
    private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes

    fun notifyUserTouch() {
        lastTouchTime = SystemClock.uptimeMillis()
    }

    fun start(context: Context) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(30_000L)
                try {
                    if (isNightTime()) {
                        val idleDuration = SystemClock.uptimeMillis() - lastTouchTime
                        if (idleDuration >= IDLE_TIMEOUT_MS) {
                            Log.d(TAG, "Night auto-lock triggered due to 5 mins inactivity at night.")
                            lockPhone(appContext)
                            lastTouchTime = SystemClock.uptimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Night auto-lock error: ${e.message}")
                }
            }
        }
    }

    private fun isNightTime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 6
    }

    private fun lockPhone(context: Context) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val comp = ComponentName(context, dev.voxpilot.assistant.service.VoxPilotDeviceAdminReceiver::class.java)
            if (dpm != null && dpm.isAdminActive(comp)) {
                dpm.lockNow()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dev.voxpilot.assistant.service.AccessibilityHelperService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lock phone: ${e.message}")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
