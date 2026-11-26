package dev.voxpilot.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AIProxyCallManager {
    private const val PREFS_NAME = "voxpilot_proxy_calls"

    fun saveSummary(context: Context, contactName: String, topic: String, summaryText: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString("proxy_history", "[]") ?: "[]"
        val array = JSONArray(existingJson)

        val entry = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("contact", contactName)
            put("topic", topic)
            put("summary", summaryText)
        }

        array.put(entry)
        prefs.edit().putString("proxy_history", array.toString()).apply()
    }

    fun getLatestSummary(context: Context, contactName: String = ""): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString("proxy_history", "[]") ?: "[]"
        val array = JSONArray(existingJson)

        if (array.length() == 0) return "Abhi tak koi proxy call ki baatcheet recorded nahi hai."

        if (contactName.isNotBlank()) {
            for (i in array.length() - 1 downTo 0) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString("contact").contains(contactName, ignoreCase = true)) {
                    val summary = obj.optString("summary")
                    val topic = obj.optString("topic")
                    return "${obj.optString("contact")} se '$topic' par ye baat hui thi: $summary"
                }
            }
        }

        val lastObj = array.optJSONObject(array.length() - 1) ?: return "Koi record nahi mila."
        return "${lastObj.optString("contact")} se '${lastObj.optString("topic")}' par ye baat hui thi: ${lastObj.optString("summary")}"
    }
}
