package dev.voxpilot.assistant.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object PhishingHistoryManager {
    private const val PREFS_NAME = "voxpilot_phishing_prefs"
    private const val KEY_HISTORY = "phishing_history"

    data class ScanRecord(
        val url: String,
        val riskLevel: String, // HIGH, MEDIUM, LOW, UNKNOWN
        val summary: String,
        val indicators: List<String>,
        val timestamp: Long
    )

    fun saveRecord(context: Context, record: ScanRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getHistory(context).toMutableList()

        // Remove duplicate if same URL exists
        existing.removeAll { it.url.equals(record.url, true) }
        existing.add(0, record) // Add to top

        val jsonArray = JSONArray()
        for (item in existing) {
            val obj = JSONObject().apply {
                put("url", item.url)
                put("riskLevel", item.riskLevel)
                put("summary", item.summary)
                put("timestamp", item.timestamp)
                val indArray = JSONArray()
                for (ind in item.indicators) indArray.put(ind)
                put("indicators", indArray)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun getHistory(context: Context): List<ScanRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<ScanRecord>()
        try {
            val jsonArray = JSONArray(stored)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val url = obj.getString("url")
                val riskLevel = obj.getString("riskLevel")
                val summary = obj.getString("summary")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val indArray = obj.optJSONArray("indicators")
                val indicators = mutableListOf<String>()
                if (indArray != null) {
                    for (j in 0 until indArray.length()) {
                        indicators.add(indArray.getString(j))
                    }
                }
                list.add(ScanRecord(url, riskLevel, summary, indicators, timestamp))
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteRecord(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getHistory(context).toMutableList()
        existing.removeAll { it.url.equals(url, true) }

        val jsonArray = JSONArray()
        for (item in existing) {
            val obj = JSONObject().apply {
                put("url", item.url)
                put("riskLevel", item.riskLevel)
                put("summary", item.summary)
                put("timestamp", item.timestamp)
                val indArray = JSONArray()
                for (ind in item.indicators) indArray.put(ind)
                put("indicators", indArray)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun clearHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }
}
