package dev.voxpilot.assistant.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.voxpilot.assistant.ui.main.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One alarm or timer. [days] are Calendar.MONDAY.. values; empty = ring once. [timerAtMs] > 0 marks a timer. */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: Set<Int>,
    val timerAtMs: Long = 0L,
) {
    val isTimer get() = timerAtMs > 0L

    fun toJson() = JSONObject().put("id", id).put("hour", hour).put("minute", minute).put("label", label)
        .put("days", JSONArray(days.toList())).put("timerAtMs", timerAtMs)

    companion object {
        fun fromJson(o: JSONObject): Alarm {
            val d = HashSet<Int>()
            o.optJSONArray("days")?.let { for (i in 0 until it.length()) d.add(it.getInt(i)) }
            return Alarm(o.getInt("id"), o.optInt("hour"), o.optInt("minute"), o.optString("label"), d, o.optLong("timerAtMs"))
        }
    }
}

/**
 * VoxPilot's own alarms: scheduled with AlarmManager.setAlarmClock (exact, survives Doze, needs no
 * special permission) and rung by [AlarmRingService]. Nothing opens the system Clock app, so an
 * alarm is set silently in the background.
 */
object AlarmScheduler {
    private const val PREFS = "voxpilot_alarms"
    private const val KEY = "alarms"
    const val ACTION_FIRE = "dev.voxpilot.assistant.ALARM_FIRE"
    const val EXTRA_ID = "alarm_id"

    // ---- storage ------------------------------------------------------------------------

    @Synchronized
    fun all(context: Context): List<Alarm> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun save(context: Context, list: List<Alarm>) {
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun find(context: Context, id: Int): Alarm? = all(context).firstOrNull { it.id == id }

    // ---- timing ---------------------------------------------------------------------------

    /** Next moment [alarm] should ring, strictly after [now]. */
    fun nextTrigger(alarm: Alarm, now: Long = System.currentTimeMillis()): Long {
        if (alarm.isTimer) return alarm.timerAtMs
        val cal = Calendar.getInstance()
        for (offset in 0..7) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            cal.set(Calendar.MINUTE, alarm.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val dayOk = alarm.days.isEmpty() || cal.get(Calendar.DAY_OF_WEEK) in alarm.days
            if (dayOk && cal.timeInMillis > now) return cal.timeInMillis
        }
        return now + 24 * 60 * 60 * 1000L
    }

    /** "today 06:00", "tomorrow 06:00", or "Mon 06:00". */
    fun describe(alarm: Alarm): String {
        val at = nextTrigger(alarm)
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(at))
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = at }
        val dayWord = when {
            today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) && today.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> "today"
            else -> {
                today.add(Calendar.DAY_OF_YEAR, 1)
                if (today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) "tomorrow"
                else SimpleDateFormat("EEE", Locale.getDefault()).format(Date(at))
            }
        }
        return "$dayWord $time"
    }

    // ---- scheduling -------------------------------------------------------------------------

    private fun firePending(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("voxpilot-alarm://$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun schedule(context: Context, alarm: Alarm) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger(alarm), show), firePending(context, alarm.id))
    }

    private fun unschedule(context: Context, id: Int) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(firePending(context, id))
    }

    private fun newId(list: List<Alarm>): Int = (list.maxOfOrNull { it.id } ?: 100) + 1

    fun add(context: Context, hour: Int, minute: Int, label: String, days: Set<Int>): Alarm {
        val list = all(context)
        // Same time + days again would just ring twice; reuse the existing one.
        list.firstOrNull { !it.isTimer && it.hour == hour && it.minute == minute && it.days == days }?.let {
            val updated = it.copy(label = label.ifBlank { it.label })
            save(context, list.map { a -> if (a.id == it.id) updated else a })
            schedule(context, updated)
            return updated
        }
        val alarm = Alarm(newId(list), hour, minute, label, days)
        save(context, list + alarm)
        schedule(context, alarm)
        return alarm
    }

    fun addTimer(context: Context, seconds: Int, label: String): Alarm {
        val list = all(context)
        val alarm = Alarm(newId(list), 0, 0, label.ifBlank { "Timer" }, emptySet(), System.currentTimeMillis() + seconds * 1000L)
        save(context, list + alarm)
        schedule(context, alarm)
        return alarm
    }

    fun cancel(context: Context, id: Int) {
        unschedule(context, id)
        save(context, all(context).filterNot { it.id == id })
    }

    /** Cancels alarms at hour:minute (or, with no time, the next alarm to ring). Returns what was removed. */
    fun cancelMatching(context: Context, hour: Int?, minute: Int?): List<Alarm> {
        val list = all(context).filterNot { it.isTimer }
        val victims = if (hour != null) {
            list.filter { it.hour == hour && it.minute == (minute ?: 0) }
        } else {
            listOfNotNull(list.minByOrNull { nextTrigger(it) })
        }
        victims.forEach { cancel(context, it.id) }
        return victims
    }

    fun cancelAll(context: Context): Int {
        val list = all(context)
        list.forEach { unschedule(context, it.id) }
        save(context, emptyList())
        return list.size
    }

    /** After boot or an app update the AlarmManager forgets everything; rebuild it from storage. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        val keep = ArrayList<Alarm>()
        for (a in all(context)) {
            if (a.isTimer && a.timerAtMs <= now) continue // a timer that expired while the phone was off
            keep.add(a)
            schedule(context, a)
        }
        save(context, keep)
    }

    /** Called when an alarm has fired: repeating ones are scheduled again, one-shot ones are removed. */
    fun onFired(context: Context, id: Int) {
        val alarm = find(context, id) ?: return
        if (alarm.days.isNotEmpty()) schedule(context, alarm) else cancel(context, id)
    }

    /** Rings again in [minutes] as a fresh one-shot. */
    fun snooze(context: Context, label: String, minutes: Int = 10): Alarm {
        val list = all(context)
        val alarm = Alarm(newId(list), 0, 0, label.ifBlank { "Alarm" }, emptySet(), System.currentTimeMillis() + minutes * 60_000L)
        save(context, list + alarm)
        schedule(context, alarm)
        return alarm
    }
}
