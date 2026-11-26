package dev.voxpilot.assistant.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PhishingGuardManager {
    private const val PREFS_NAME = "voxpilot_sms_guard_prefs"
    private const val KEY_ALERTS = "sms_alerts"

    data class SmsAlert(val sender: String, val body: String, val timestamp: Long)

    fun logAlert(context: Context, sender: String, body: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getAlerts(context).toMutableList()

        existing.add(0, SmsAlert(sender, body, System.currentTimeMillis()))
        if (existing.size > 50) existing.removeAt(existing.size - 1)

        val arr = JSONArray()
        for (a in existing) {
            val obj = JSONObject().apply {
                put("sender", a.sender)
                put("body", a.body)
                put("timestamp", a.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ALERTS, arr.toString()).apply()
    }

    fun getAlerts(context: Context): List<SmsAlert> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ALERTS, null) ?: return emptyList()
        val list = mutableListOf<SmsAlert>()
        try {
            val arr = JSONArray(stored)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SmsAlert(
                        sender = obj.optString("sender", "Unknown"),
                        body = obj.optString("body", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun clearAlerts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ALERTS).apply()
    }
}
