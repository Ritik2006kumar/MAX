package dev.voxpilot.assistant.live

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Resolves spoken app names to installed packages. Built once and cached - a cold scan takes seconds. */
object Apps {
    private const val TAG = "Apps"
    private const val TTL_MS = 15 * 60_000L

    @Volatile private var cache: Map<String, String>? = null
    @Volatile private var builtAt = 0L
    private val mutex = Mutex()

    /** Spoken names -> package, for apps people call something other than their on-phone label. */
    private val known = mapOf(
        "whatsapp" to "com.whatsapp", "whatsapp business" to "com.whatsapp.w4b", "व्हाट्सएप" to "com.whatsapp",
        "youtube" to "com.google.android.youtube", "you tube" to "com.google.android.youtube", "यूट्यूब" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "instagram" to "com.instagram.android", "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana", "messenger" to "com.facebook.orca",
        "chrome" to "com.android.chrome", "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps", "google maps" to "com.google.android.apps.maps",
        "play store" to "com.android.vending", "playstore" to "com.android.vending",
        "telegram" to "org.telegram.messenger", "spotify" to "com.spotify.music",
        "snapchat" to "com.snapchat.android", "twitter" to "com.twitter.android", "x" to "com.twitter.android",
        "amazon" to "in.amazon.mShop.android.shopping", "flipkart" to "com.flipkart.android",
        "phonepe" to "com.phonepe.app", "paytm" to "net.one97.paytm",
        "google pay" to "com.google.android.apps.nbu.paisa.user", "gpay" to "com.google.android.apps.nbu.paisa.user",
        "photos" to "com.google.android.apps.photos", "google photos" to "com.google.android.apps.photos",
        "drive" to "com.google.android.apps.docs", "meet" to "com.google.android.apps.tachyon",
        "calculator" to "com.google.android.calculator", "calendar" to "com.google.android.calendar",
        "where is my train" to "com.whereismytrain.android", "ixigo" to "com.ixigo.train.app",
        "confirmtkt" to "com.confirmtkt.lite", "irctc" to "cris.org.in.prs.ima",
    )

    private fun fresh() = cache?.isNotEmpty() == true && System.currentTimeMillis() - builtAt < TTL_MS

    fun prewarm(context: Context) {
        if (fresh()) return
        Thread { runCatching { build(context.applicationContext) } }.apply { isDaemon = true }.start()
    }

    private fun build(context: Context): Map<String, String> {
        if (fresh()) return cache!!
        synchronized(this) {
            if (fresh()) return cache!!
            val pm = context.packageManager
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION") pm.getInstalledApplications(0)
            }
            val map = HashMap<String, String>(apps.size)
            for (info in apps) {
                try {
                    if (pm.getLaunchIntentForPackage(info.packageName) == null) continue
                    val label = pm.getApplicationLabel(info).toString().lowercase().trim()
                    if (label.isNotEmpty() && label !in map) map[label] = info.packageName
                } catch (_: Exception) {
                }
            }
            cache = map
            builtAt = System.currentTimeMillis()
            Log.d(TAG, "app cache built: ${map.size}")
            return map
        }
    }

    private val filler = setOf("app", "application", "the", "open", "launch", "start", "kholo", "khol", "chalu", "karo", "kar", "do", "ko", "ka", "ki", "mein", "me")

    /** Lowercase, accents/punctuation removed, filler words dropped: "Open the You-Tube app" -> "youtube". */
    private fun norm(text: String): String {
        val plain = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val words = plain.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in filler }
        return words.joinToString(" ")
    }

    private fun squash(text: String) = text.replace(" ", "")

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return 1.0 - dp[b.length].toDouble() / maxOf(a.length, b.length)
    }

    /** 0..1 score of how well [label] answers the spoken [q] (both already normalised). */
    private fun score(q: String, label: String): Double {
        if (label == q) return 1.0
        val qs = squash(q)
        val ls = squash(label)
        if (ls == qs) return 0.98
        if (label.startsWith(q) || q.startsWith(label)) return 0.9
        val qw = q.split(' ').toSet()
        val lw = label.split(' ').toSet()
        if (qw.isNotEmpty() && qw.all { w -> lw.any { it.startsWith(w) || w.startsWith(it) } }) return 0.85
        if (ls.contains(qs) && qs.length >= 4) return 0.8
        return similarity(qs, ls) * 0.75 // spelling / mishearing ("whats app", "instagrm")
    }

    private suspend fun labels(context: Context, forceRefresh: Boolean = false): Map<String, String> {
        if (forceRefresh) { cache = null; builtAt = 0L }
        return if (fresh()) cache!! else mutex.withLock { withContext(Dispatchers.IO) { build(context.applicationContext) } }
    }

    /** Returns the package for [query], or null. */
    suspend fun find(context: Context, query: String): String? {
        val q = norm(query)
        if (q.isEmpty()) return null
        val pm = context.packageManager
        (known[q] ?: known[query.lowercase().trim()])?.let { if (pm.getLaunchIntentForPackage(it) != null) return it }

        for (attempt in 0..1) {
            // Second pass re-reads the installed list: the app may have been installed after the cache was built.
            val all = labels(context, forceRefresh = attempt == 1)
            val best = all.entries
                .map { (label, pkg) -> Triple(label, pkg, score(q, norm(label))) }
                .maxByOrNull { it.third }
            if (best != null && best.third >= 0.72) return best.second
            // Package id as a last resort: "com.spotify.music" for "spotify".
            all.values.firstOrNull { it.lowercase().split('.').any { part -> part == squash(q) } }?.let { return it }
        }
        return null
    }

    /** Closest installed app names to [query], so the model can retry with a real one. */
    suspend fun suggestions(context: Context, query: String, limit: Int = 6): List<String> {
        val q = norm(query)
        return labels(context).keys
            .map { it to score(q, norm(it)) }
            .filter { it.second > 0.35 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun labelOf(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: Exception) {
        false
    }

    /** Built-in screens by name, via standard actions - no package lookup needed. Returns the intent and a friendly name. */
    fun systemShortcut(query: String): Pair<String, Intent>? {
        val q = query.lowercase().trim()
        return when {
            q in setOf("camera", "कैमरा") -> "Camera" to Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            q in setOf("settings", "setting", "सेटिंग", "सेटिंग्स") -> "Settings" to Intent(Settings.ACTION_SETTINGS)
            q in setOf("phone", "dialer", "dial pad", "keypad", "फोन", "फ़ोन") -> "Phone" to Intent(Intent.ACTION_DIAL)
            q in setOf("contacts", "contact", "कॉन्टैक्ट्स") -> "Contacts" to Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
            q in setOf("messages", "sms", "message", "मैसेज") -> "Messages" to Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            q in setOf("browser", "internet", "web") -> "Browser" to Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            q in setOf("gallery", "photos app") -> "Gallery" to Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            q in setOf("alarm", "alarms", "clock", "घड़ी") -> "Clock" to Intent(AlarmClock.ACTION_SHOW_ALARMS)
            else -> null
        }
    }
}
