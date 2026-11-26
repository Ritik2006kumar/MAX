package dev.voxpilot.assistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderScheduler {

    private const val PREFS = "voxpilot_reminders"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TEXT = "reminder_text"

    fun schedule(context: Context, text: String, triggerAtMillis: Long): Long {
        val id = System.currentTimeMillis()
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return id

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TEXT, text)
        }

        val pi = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }

        saveReminder(context, id, text, triggerAtMillis)
        return id
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        removeReminder(context, id)
    }

    fun listText(context: Context): String {
        val list = loadReminders(context)
        if (list.length() == 0) return "No reminders currently set."
        val sdf = SimpleDateFormat("EEE hh:mm a", Locale.getDefault())
        val sb = StringBuilder()
        for (i in 0 until list.length()) {
            val obj = list.optJSONObject(i) ?: continue
            val id = obj.optLong("id")
            val text = obj.optString("text")
            val time = sdf.format(Date(obj.optLong("time")))
            sb.append("• $text at $time (ID: $id)\n")
        }
        return sb.toString().trim()
    }

    private fun saveReminder(context: Context, id: Long, text: String, time: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = loadReminders(context)
        array.put(JSONObject().put("id", id).put("text", text).put("time", time))
        prefs.edit().putString("list", array.toString()).apply()
    }

    fun removeReminder(context: Context, id: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = loadReminders(context)
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optLong("id") != id) newArray.put(obj)
        }
        prefs.edit().putString("list", newArray.toString()).apply()
    }

    fun loadReminders(context: Context): JSONArray {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", "[]") ?: "[]"
        return try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    }
}
