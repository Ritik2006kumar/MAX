package dev.voxpilot.assistant.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Turn(val role: String, val text: String, val timestamp: Long = System.currentTimeMillis())

object ConversationMemory {
    private val history = mutableListOf<Turn>()
    private const val MAX_TURNS = 40
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadFromStorage(context)
    }

    fun addUser(context: Context, text: String) {
        init(context)
        history.add(Turn("user", text))
        trimAndSave(context)
    }

    fun addAssistant(context: Context, text: String) {
        init(context)
        history.add(Turn("assistant", text))
        trimAndSave(context)
    }

    fun getRecent(n: Int = 12): List<Turn> = history.takeLast(n)

    fun clear(context: Context) {
        history.clear()
        saveToStorage(context)
    }

    private fun trimAndSave(context: Context) {
        if (history.size > MAX_TURNS) {
            history.removeAt(0)
        }
        saveToStorage(context)
    }

    private fun saveToStorage(context: Context) {
        try {
            val prefs = context.getSharedPreferences("voxpilot_memory", Context.MODE_PRIVATE)
            val array = JSONArray()
            for (turn in history) {
                array.put(JSONObject().apply {
                    put("role", turn.role)
                    put("text", turn.text)
                    put("ts", turn.timestamp)
                })
            }
            prefs.edit().putString("history_json", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun loadFromStorage(context: Context) {
        try {
            val prefs = context.getSharedPreferences("voxpilot_memory", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("history_json", null) ?: return
            val array = JSONArray(jsonStr)
            history.clear()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                history.add(Turn(obj.optString("role"), obj.optString("text"), obj.optLong("ts")))
            }
        } catch (_: Exception) {}
    }

    fun getContextSummary(context: Context? = null): String {
        context?.let { init(it) }
        if (history.isEmpty()) return ""
        return history.takeLast(12).joinToString("\n") {
            "${if (it.role == "user") "User" else "Max"}: ${it.text}"
        }
    }
}
