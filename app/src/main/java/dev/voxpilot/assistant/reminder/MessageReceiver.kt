package dev.voxpilot.assistant.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log

class MessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val contact = intent.getStringExtra(MessageScheduler.EXTRA_CONTACT) ?: return
        val message = intent.getStringExtra(MessageScheduler.EXTRA_MESSAGE) ?: return
        val app = intent.getStringExtra(MessageScheduler.EXTRA_APP) ?: "whatsapp"

        try {
            if (app.lowercase().contains("sms")) {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(contact, null, message, null, null)
            } else {
                val encoded = Uri.encode(message)
                val url = "https://api.whatsapp.com/send?phone=$contact&text=$encoded"
                val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(waIntent)
            }
            Log.d("MessageReceiver", "Scheduled message sent successfully to $contact")
        } catch (e: Exception) {
            Log.e("MessageReceiver", "Scheduled message failed: ${e.message}")
        }
    }
}
