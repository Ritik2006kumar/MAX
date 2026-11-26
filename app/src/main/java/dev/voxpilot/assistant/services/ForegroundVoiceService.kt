package dev.voxpilot.assistant.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.live.LiveSession
import dev.voxpilot.assistant.ui.main.MainActivity
import dev.voxpilot.assistant.utils.Constants

/**
 * Keeps VoxPilot's live voice conversation alive in the background. The conversation itself
 * (Gemini Live + mic + speaker + device tools) lives in [LiveSession]; this service is what tells
 * Android not to kill the process while the mic is open.
 */
class ForegroundVoiceService : Service() {

    companion object {
        var instance: ForegroundVoiceService? = null
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Constants.NOTIF_ID_VOICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(Constants.NOTIF_ID_VOICE, notification)
        }
        LiveSession.start(this)
    }

    /** Typed message from the on-screen button, sent into the live conversation. */
    fun sendTextToGemini(text: String) = LiveSession.sendText(text)

    fun reconnectGemini() = LiveSession.reconnect()

    private fun buildNotification(): Notification {
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_VOICE)
            .setContentTitle("VoxPilot is listening ❤️")
            .setContentText("Always ready for you")
            .setSmallIcon(R.drawable.ic_voxpilot_notif)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(Constants.NOTIF_CHANNEL_VOICE, "VoxPilot Voice Service", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Idempotent: a repeated start (BootReceiver, Activity) must not open a second session.
        LiveSession.start(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        instance = null
        LiveSession.stop()
        super.onDestroy()
    }
}
