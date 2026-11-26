package dev.voxpilot.assistant.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import dev.voxpilot.assistant.R
import dev.voxpilot.assistant.automation.FindMyPhoneManager

class FindMyPhoneActivity : AppCompatActivity() {

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_my_phone)

        val stopBtn = findViewById<Button>(R.id.stopFindBtn)
        stopBtn.setOnClickListener {
            FindMyPhoneManager.stop(this)
            finish()
        }

        FindMyPhoneManager.start(this)

        val filter = IntentFilter("VOXPILOT_STOP_FIND_PHONE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onBackPressed() {
        FindMyPhoneManager.stop(this)
        super.onBackPressed()
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        FindMyPhoneManager.stop(this)
        super.onDestroy()
    }
}
