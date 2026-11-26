package dev.voxpilot.assistant.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Full-screen "alarm is ringing" screen with Dismiss and Snooze, shown over the lock screen. */
class AlarmRingActivity : AppCompatActivity() {

    private val stopped = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT

        val label = intent.getStringExtra(AlarmRingService.EXTRA_LABEL).orEmpty().ifBlank { "Alarm" }
        val time = intent.getStringExtra(AlarmRingService.EXTRA_TIME).orEmpty()
        val timer = intent.getBooleanExtra(AlarmRingService.EXTRA_TIMER, false)
        val d = resources.displayMetrics.density

        fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
            setColor(fill); cornerRadius = 30 * d; setStroke((1.5f * d).toInt(), stroke)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#080508"))
            setPadding((28 * d).toInt(), 0, (28 * d).toInt(), 0)
        }
        root.addView(TextView(this).apply {
            text = if (timer) "TIMER" else "ALARM"
            setTextColor(Color.parseColor("#FF5C7C")); textSize = 14f; letterSpacing = 0.35f
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = if (timer) "Time's up" else time
            setTextColor(Color.WHITE); textSize = 76f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER; setShadowLayer(24f, 0f, 0f, Color.parseColor("#FF2D55"))
            setPadding(0, (12 * d).toInt(), 0, (6 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = label; setTextColor(Color.parseColor("#C9C9D2")); textSize = 20f; gravity = Gravity.CENTER
        })

        val gap = (64 * d).toInt()
        root.addView(Button(this).apply {
            text = "Dismiss"; isAllCaps = false; textSize = 18f; setTextColor(Color.WHITE); stateListAnimator = null
            background = pill(Color.parseColor("#FF2D55"), Color.parseColor("#FF2D55"))
            setOnClickListener { startService(Intent(this@AlarmRingActivity, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_DISMISS)); finish() }
        }, LinearLayout.LayoutParams(-1, (64 * d).toInt()).apply { topMargin = gap })
        root.addView(Button(this).apply {
            text = "Snooze 10 min"; isAllCaps = false; textSize = 16f; setTextColor(Color.parseColor("#FF5C7C")); stateListAnimator = null
            background = pill(Color.parseColor("#14FF2D55"), Color.parseColor("#66FF2D55"))
            setOnClickListener { startService(Intent(this@AlarmRingActivity, AlarmRingService::class.java).setAction(AlarmRingService.ACTION_SNOOZE)); finish() }
        }, LinearLayout.LayoutParams(-1, (56 * d).toInt()).apply { topMargin = (14 * d).toInt() })
        setContentView(root)

        val filter = IntentFilter(AlarmRingService.ACTION_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(stopped, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(stopped, filter)
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopped) } catch (_: Exception) {}
        super.onDestroy()
    }

    @Deprecated("Back must not silence a ringing alarm by accident")
    override fun onBackPressed() {}
}
