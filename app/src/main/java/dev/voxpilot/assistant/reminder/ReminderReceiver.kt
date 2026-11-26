package dev.voxpilot.assistant.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.utils.prefs
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "voxpilot_reminders_channel"
        private const val NOTIF_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L)
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT) ?: "Reminder"

        ReminderScheduler.removeReminder(context, id)

        val prefs = context.prefs()
        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val userName = prefs.getString("user_name", "Sir").orEmpty().ifBlank { "Sir" }

        val spokenMsg = when (personality) {
            "gf" -> "$userName, ye aapka reminder hai: $text"
            "professional" -> "Reminder for $userName: $text"
            "caring" -> "$userName, yaad dila rahi hoon: $text"
            "emotional" -> "$userName, jo aapne kaha tha wo reminder aa gaya hai: $text"
            "nautanki" -> "Attention $userName! Dhishoom! Aapse judi zaroori cheez: $text"
            else -> "Reminder: $text"
        }

        showNotification(context, text)
        speakOut(context.applicationContext, spokenMsg)
    }

    private fun showNotification(context: Context, text: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "VoxPilot Reminders", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.img)
            .setContentTitle("VoxPilot Reminder 🔔")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(NOTIF_ID, notif)
    }

    private fun speakOut(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "REMINDER_SPEECH")
            }
        }
    }
}
