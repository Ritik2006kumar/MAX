package dev.voxpilot.assistant.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.voxpilot.assistant.R

/** Plays the alarm sound (looping, alarm volume) with vibration until dismissed, snoozed, or 2 minutes pass. */
class AlarmRingService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var label = "Alarm"
    private var isTimer = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> { stopRinging(); return START_NOT_STICKY }
            ACTION_SNOOZE -> {
                AlarmScheduler.snooze(this, label)
                stopRinging()
                return START_NOT_STICKY
            }
        }
        label = intent?.getStringExtra(EXTRA_LABEL).orEmpty().ifBlank { "Alarm" }
        isTimer = intent?.getBooleanExtra(EXTRA_TIMER, false) == true
        val time = intent?.getStringExtra(EXTRA_TIME).orEmpty()

        val notification = buildNotification(time)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        startSound()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopRinging() }, 2 * 60 * 1000L)
        return START_NOT_STICKY
    }

    private fun startSound() {
        stopSound()
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "alarm sound failed: ${e.message}")
        }
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 700, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
    }

    private fun stopSound() {
        player?.apply { try { stop() } catch (_: Exception) {}; release() }
        player = null
        (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.cancel()
    }

    private fun stopRinging() {
        handler.removeCallbacksAndMessages(null)
        stopSound()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun serviceAction(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, AlarmRingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(time: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "VoxPilot alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null) // the service plays the sound itself
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        val ring = Intent(this, AlarmRingActivity::class.java)
            .putExtra(EXTRA_LABEL, label).putExtra(EXTRA_TIME, time).putExtra(EXTRA_TIMER, isTimer)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullScreen = PendingIntent.getActivity(this, 1, ring, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_voxpilot_notif)
            .setContentTitle(if (isTimer) "Timer done" else "Alarm $time".trim())
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze 10 min", serviceAction(ACTION_SNOOZE, 2))
            .addAction(0, "Dismiss", serviceAction(ACTION_DISMISS, 3))
            .build()
    }

    override fun onDestroy() {
        stopSound()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmRing"
        private const val CHANNEL = "voxpilot_alarm_channel"
        private const val NOTIF_ID = 4242
        const val ACTION_DISMISS = "dev.voxpilot.assistant.ALARM_DISMISS"
        const val ACTION_SNOOZE = "dev.voxpilot.assistant.ALARM_SNOOZE"
        const val ACTION_STOPPED = "dev.voxpilot.assistant.ALARM_STOPPED"
        const val EXTRA_LABEL = "label"
        const val EXTRA_TIME = "time"
        const val EXTRA_TIMER = "timer"
    }
}
