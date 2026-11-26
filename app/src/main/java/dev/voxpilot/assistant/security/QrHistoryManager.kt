package dev.voxpilot.assistant.security

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object QrHistoryManager {
    private const val PREFS_NAME = "voxpilot_qr_prefs"
    private const val KEY_HISTORY = "qr_history"

    data class QrRecord(
        val rawContent: String,
        val category: String, // URL, WIFI, CONTACT, TEXT
        val riskLevel: String, // HIGH, MEDIUM, LOW, UNKNOWN
        val summary: String,
        val details: List<String>,
        val timestamp: Long
    )

    fun saveRecord(context: Context, record: QrRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getHistory(context).toMutableList()

        existing.removeAll { it.rawContent.equals(record.rawContent, true) }
        existing.add(0, record)

        val jsonArray = JSONArray()
        for (item in existing) {
            val obj = JSONObject().apply {
                put("rawContent", item.rawContent)
                put("category", item.category)
                put("riskLevel", item.riskLevel)
                put("summary", item.summary)
                put("timestamp", item.timestamp)
                val detArray = JSONArray()
                for (d in item.details) detArray.put(d)
                put("details", detArray)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun getHistory(context: Context): List<QrRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<QrRecord>()
        try {
            val jsonArray = JSONArray(stored)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rawContent = obj.getString("rawContent")
                val category = obj.optString("category", "TEXT")
                val riskLevel = obj.optString("riskLevel", "LOW")
                val summary = obj.optString("summary", "")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val detArray = obj.optJSONArray("details")
                val details = mutableListOf<String>()
                if (detArray != null) {
                    for (j in 0 until detArray.length()) {
                        details.add(detArray.getString(j))
                    }
                }
                list.add(QrRecord(rawContent, category, riskLevel, summary, details, timestamp))
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteRecord(context: Context, rawContent: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getHistory(context).toMutableList()
        existing.removeAll { it.rawContent.equals(rawContent, true) }

        val jsonArray = JSONArray()
        for (item in existing) {
            val obj = JSONObject().apply {
                put("rawContent", item.rawContent)
                put("category", item.category)
                put("riskLevel", item.riskLevel)
                put("summary", item.summary)
                put("timestamp", item.timestamp)
                val detArray = JSONArray()
                for (d in item.details) detArray.put(d)
                put("details", detArray)
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
