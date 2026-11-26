package dev.voxpilot.assistant.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Fires when an alarm is due, and rebuilds the schedule after a reboot / app update. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AlarmScheduler.ACTION_FIRE -> {
                val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
                val alarm = AlarmScheduler.find(context, id) ?: return
                // Repeating alarms are re-armed first so a crash while ringing cannot lose tomorrow's alarm.
                AlarmScheduler.onFired(context, id)
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AlarmRingService::class.java)
                        .putExtra(AlarmRingService.EXTRA_LABEL, alarm.label)
                        .putExtra(AlarmRingService.EXTRA_TIMER, alarm.isTimer)
                        .putExtra(AlarmRingService.EXTRA_TIME, if (alarm.isTimer) "" else String.format("%02d:%02d", alarm.hour, alarm.minute)),
                )
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AlarmScheduler.rescheduleAll(context)
        }
    }
}
