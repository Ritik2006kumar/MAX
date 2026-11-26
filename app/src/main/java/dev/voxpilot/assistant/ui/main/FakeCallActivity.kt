package dev.voxpilot.assistant.ui.main

import android.app.KeyguardManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R

class FakeCallActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_fake_call)

        val callerName = intent.getStringExtra("CALLER_NAME") ?: "Boss"
        val phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: "+91 98765 43210"

        findViewById<TextView>(R.id.callerNameText).text = callerName
        findViewById<TextView>(R.id.phoneNumberText).text = phoneNumber

        findViewById<ImageButton>(R.id.declineBtn).setOnClickListener {
            stopRingingAndFinish()
        }

        findViewById<ImageButton>(R.id.acceptBtn).setOnClickListener {
            stopRingingAndFinish()
        }

        startRinging()
    }

    private fun startRinging() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRingingAndFinish() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (_: Exception) {}
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingingAndFinish()
    }
}
