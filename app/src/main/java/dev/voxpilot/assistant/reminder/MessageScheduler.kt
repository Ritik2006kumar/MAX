package dev.voxpilot.assistant.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object MessageScheduler {

    const val EXTRA_CONTACT = "sched_contact"
    const val EXTRA_MESSAGE = "sched_message"
    const val EXTRA_APP = "sched_app"

    fun schedule(context: Context, contact: String, message: String, app: String, triggerAtMillis: Long): Long {
        val id = System.currentTimeMillis() + 1
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return id

        val intent = Intent(context, MessageReceiver::class.java).apply {
            putExtra(EXTRA_CONTACT, contact)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_APP, app)
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

        return id
    }
}
