package dev.voxpilot.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import dev.voxpilot.assistant.utils.prefs
import java.util.Locale

class BatteryAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = context.prefs()
        val personality = prefs.getString("personality_mode", "gf") ?: "gf"
        val userName = prefs.getString("user_name", "Sir").orEmpty().ifBlank { "Sir" }

        val message = when (action) {
            Intent.ACTION_POWER_CONNECTED -> when (personality) {
                "gf" -> "Charger connect ho gaya $userName, ab main charge ho rahi hoon!"
                "professional" -> "Charger connected."
                "caring" -> "Charger laga diya hai $userName, ab thodi der me full charge ho jayega."
                "emotional" -> "Shukr hai charger laga diya $userName, meri battery khatam ho rahi thi."
                "nautanki" -> "Aaha! Bijli ka jhatka, charger lag gaya re baba!"
                else -> "Charger connected."
            }
            Intent.ACTION_POWER_DISCONNECTED -> when (personality) {
                "gf" -> "Charger nikal diya $userName? Battery dhyaan rakhna!"
                "professional" -> "Charger disconnected."
                "caring" -> "Charger nikal diya gaya hai $userName."
                "emotional" -> "Aise kaise charger nikal diya $userName?"
                "nautanki" -> "Oye hoye! Bijli kat gayi, charger nikal gaya!"
                else -> "Charger disconnected."
            }
            Intent.ACTION_BATTERY_LOW -> when (personality) {
                "gf" -> "$userName charger laga do na, battery khatam hone wali hai!"
                "professional" -> "Battery low. Please connect charger."
                "caring" -> "$userName, battery low ho gayi hai, kripya charger laga lijiye."
                "emotional" -> "$userName meri battery low ho gayi hai, mujhe akela mat chhodo!"
                "nautanki" -> "Daya! Battery khatam hone wali hai, jaldi charger lao!"
                else -> "Battery low. Please charge your phone."
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL

                if (batteryPct == 100 && isCharging) {
                    val alreadyAlerted = prefs.getBoolean("battery_full_alerted", false)
                    if (!alreadyAlerted) {
                        prefs.edit().putBoolean("battery_full_alerted", true).apply()
                        when (personality) {
                            "gf" -> "$userName! Battery 100% full charge ho gayi hai, kripya charger nikal do!"
                            "professional" -> "Battery is 100% fully charged. Please disconnect the charger."
                            "caring" -> "$userName, aapki battery 100% full ho gayi hai, ab charger nikal lijiye."
                            "emotional" -> "$userName, main 100% full charge ho gayi hoon, ab charger nikal do na!"
                            "nautanki" -> "Shabaash! 100% battery full ho gayi, ab to charger nikal lo maalik!"
                            else -> "Battery is 100% fully charged."
                        }
                    } else return
                } else {
                    if (batteryPct < 100) {
                        prefs.edit().putBoolean("battery_full_alerted", false).apply()
                    }
                    return
                }
            }
            else -> return
        }

        speakOut(context.applicationContext, message)
    }

    private fun speakOut(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BATTERY_ALERT")
            }
        }
    }
}
