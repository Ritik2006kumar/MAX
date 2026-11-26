package dev.voxpilot.assistant.security

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object SecureVaultManager {
    private const val PREFS_NAME = "voxpilot_vault_prefs"
    private const val KEY_ITEMS = "vault_items"
    private const val KEY_VAULT_PIN = "vault_custom_pin"
    private const val KEY_INTRUDERS = "vault_intruders"

    data class VaultItem(
        val id: String,
        val title: String,
        val type: String, // "NOTE" or "MEDIA"
        val pathOrContent: String,
        val timestamp: Long
    )

    data class IntruderRecord(val imagePath: String, val timestamp: Long)

    fun setVaultPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hashed = hashPin(pin)
        prefs.edit().putString(KEY_VAULT_PIN, hashed).apply()
    }

    fun verifyVaultPin(context: Context, inputPin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_VAULT_PIN, null) ?: return false
        return hashPin(inputPin) == stored
    }

    fun hasVaultPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_VAULT_PIN)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(digest.digest(pin.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun logIntruder(context: Context, imagePath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getIntruders(context).toMutableList()
        existing.add(0, IntruderRecord(imagePath, System.currentTimeMillis()))
        if (existing.size > 20) existing.removeAt(existing.size - 1)

        val arr = JSONArray()
        for (rec in existing) {
            val obj = JSONObject().apply {
                put("imagePath", rec.imagePath)
                put("timestamp", rec.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_INTRUDERS, arr.toString()).apply()
    }

    fun getIntruders(context: Context): List<IntruderRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_INTRUDERS, null) ?: return emptyList()
        val list = mutableListOf<IntruderRecord>()
        try {
            val arr = JSONArray(stored)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    IntruderRecord(
                        imagePath = obj.getString("imagePath"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveItem(context: Context, title: String, type: String, pathOrContent: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getItems(context).toMutableList()

        val id = System.currentTimeMillis().toString()
        val storedValue = if (type == "NOTE") SecurityManager.encrypt(pathOrContent) else pathOrContent

        existing.add(0, VaultItem(id, title, type, storedValue, System.currentTimeMillis()))

        val arr = JSONArray()
        for (item in existing) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("type", item.type)
                put("pathOrContent", item.pathOrContent)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun getItems(context: Context): List<VaultItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        val list = mutableListOf<VaultItem>()
        try {
            val arr = JSONArray(stored)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VaultItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        type = obj.optString("type", "NOTE"),
                        pathOrContent = obj.getString("pathOrContent"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun deleteItem(context: Context, item: VaultItem) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getItems(context).toMutableList()
        existing.removeAll { it.id == item.id }

        if (item.type == "MEDIA") {
            try {
                val file = File(item.pathOrContent)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
        }

        val arr = JSONArray()
        for (i in existing) {
            val obj = JSONObject().apply {
                put("id", i.id)
                put("title", i.title)
                put("type", i.type)
                put("pathOrContent", i.pathOrContent)
                put("timestamp", i.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }
}
