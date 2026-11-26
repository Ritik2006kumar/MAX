package dev.voxpilot.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityHelperService : AccessibilityService() {

    companion object {
        private const val TAG = "VoxPilot_ACCESS"

        var instance: AccessibilityHelperService? = null
        var currentRoot: AccessibilityNodeInfo? = null

        fun isEnabled(context: Context): Boolean {
            val expectedComponentName =
                ComponentName(
                    context,
                    AccessibilityHelperService::class.java
                )

            val enabledServicesSetting =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

            val colonSplitter =
                TextUtils.SimpleStringSplitter(':')

            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()

                val enabledService =
                    ComponentName.unflattenFromString(
                        componentNameString
                    )

                if (enabledService == expectedComponentName) {
                    return true
                }
            }

            return false
        }

        fun getFreshRoot(): AccessibilityNodeInfo? {
            return instance?.rootInActiveWindow ?: currentRoot
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this
        SmartAccessibilityEngine.service = this

        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {

            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

            feedbackType =
                AccessibilityServiceInfo.FEEDBACK_GENERIC

            flags =
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

            notificationTimeout = 50
        }

        currentRoot = rootInActiveWindow

        Log.d(TAG, "==============================")
        Log.d(TAG, "VoxPilot ACCESSIBILITY CONNECTED")
        Log.d(TAG, "==============================")
    }

    @Volatile private var lastEventAtMs = SystemClock.uptimeMillis()

    /** Bumps on every window transition; lets callers detect "the app finished opening". */
    @Volatile var windowStamp: Long = 0
        private set

    private var tts: android.speech.tts.TextToSpeech? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        lastEventAtMs = SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            windowStamp++
            val pkg = event.packageName?.toString().orEmpty()

            val launchers = listOf("launcher", "home", "ui.launcher")
            if (launchers.any { pkg.contains(it) }) {
                dev.voxpilot.assistant.security.AppLockActivity.isUnlockedThisSession = false
            }

            if (pkg.isNotEmpty() && pkg != packageName && dev.voxpilot.assistant.security.SecurityManager.isPackageLocked(this, pkg)) {
                if (!dev.voxpilot.assistant.security.AppLockActivity.isUnlockedThisSession) {
                    dev.voxpilot.assistant.security.AppLockActivity.launch(this)
                }
            }
        }
        currentRoot = rootInActiveWindow
        dev.voxpilot.assistant.security.NightAutoLockManager.notifyUserTouch()
        dev.voxpilot.assistant.accessibility.AccessibilityEventManager.onEvent(event)

        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(dev.voxpilot.assistant.utils.Constants.KEY_MESSAGE_ANNOUNCE, true)) return
            val pkg = event.packageName?.toString().orEmpty()
            val textList = event.text
            val title = event.parcelableData?.let {
                if (it is android.app.Notification) {
                    it.extras.getString(android.app.Notification.EXTRA_TITLE)
                } else null
            } ?: "Someone"

            if (pkg.contains("whatsapp") || pkg.contains("messaging") || pkg.contains("sms") || pkg.contains("telegram") || pkg.contains(other = "instagram")) {
                val msgText = textList.joinToString(" ")
                if (msgText.isNotBlank()) {
                    dev.voxpilot.assistant.accessibility.PendingReplyManager.setPending(title, msgText, if (pkg.contains("whatsapp")) "whatsapp" else "sms")
                    speakIncomingMessageWithSuggestion(pkg, title, msgText)
                }
            }
        }
    }

    private fun speakIncomingMessageWithSuggestion(pkg: String, sender: String, text: String) {
        try {
            val appName = if (pkg.contains("whatsapp")) "WhatsApp" else "Message"
            val promptText = "Naya $appName message $sender se aaya hai: $text. Suggested reply hai: 'Theek hai, main baad me baat karta hu'. Kya ye reply bhej du?"

            val prefs = getSharedPreferences("voxpilot_prefs", Context.MODE_PRIVATE)
            val engine = prefs.getString("voice_engine", "system")
            val apiKey = prefs.getString("tts_api_key", "").orEmpty()
            val voiceId = prefs.getString("elevenlabs_voice_id", "21m00Tcm4TlvDq8ikWAM").orEmpty()

            if (engine == "elevenlabs" && apiKey.isNotBlank()) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                    dev.voxpilot.assistant.network.ElevenLabsTtsClient.speak(applicationContext, promptText, apiKey, voiceId)
                }
            } else {
                if (tts == null) {
                    tts = android.speech.tts.TextToSpeech(applicationContext) { status ->
                        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                            tts?.language = java.util.Locale("hi", "IN")
                            tts?.speak(promptText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "MSG_SUGGEST")
                        }
                    }
                } else {
                    tts?.speak(promptText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "MSG_SUGGEST")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Message suggestion TTS failed: ${e.message}")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Live-voice helpers (used by live.DeviceActions). All suspend/wait-based, no fixed sleeps.
    // ---------------------------------------------------------------------------------------

    /** Waits until no UI event arrived for [quietMs] (or [timeoutMs] passes). */
    suspend fun awaitIdle(quietMs: Long = 200, timeoutMs: Long = 1200) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val quiet = SystemClock.uptimeMillis() - lastEventAtMs
            if (quiet >= quietMs || SystemClock.uptimeMillis() >= deadline) return
            delay((quietMs - quiet).coerceIn(20L, 100L))
        }
    }

    /** Waits for [rootInActiveWindow] to exist (right after an app launch it can lag). */
    suspend fun awaitRoot(retries: Int = 8, gapMs: Long = 80): AccessibilityNodeInfo? {
        repeat(retries) {
            rootInActiveWindow?.let { return it }
            delay(gapMs)
        }
        return null
    }

    private inline fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Boolean) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (visit(node)) return
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
    }

    private fun label(node: AccessibilityNodeInfo): String =
        (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()).trim()

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var cur = node
        while (cur != null) {
            if (cur.isClickable && cur.isEnabled) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
        }
        return false
    }

    /** Clicks the first node whose text/description matches [predicate]. */
    suspend fun clickWhere(predicate: (AccessibilityNodeInfo, String) -> Boolean): Boolean {
        awaitIdle()
        val root = awaitRoot() ?: return false
        var hit: AccessibilityNodeInfo? = null
        walk(root) { n ->
            val l = label(n).lowercase()
            (l.isNotEmpty() && predicate(n, l)).also { if (it) hit = n }
        }
        return clickNodeOrParent(hit)
    }

    /** Exact label match first (so "Send" does not hit "Send to..."), then contains. */
    suspend fun clickByText(target: String, exactOnly: Boolean = false): Boolean {
        val needle = target.trim().lowercase()
        if (needle.isEmpty()) return false
        if (clickWhere { n, l -> l == needle || n.text?.toString()?.lowercase() == needle || n.contentDescription?.toString()?.lowercase() == needle }) return true
        return !exactOnly && clickWhere { _, l -> l.contains(needle) }
    }

    suspend fun typeIntoFocused(text: String, submit: Boolean = false): Boolean {
        awaitIdle()
        val root = awaitRoot() ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: run {
            var edit: AccessibilityNodeInfo? = null
            walk(root) { n -> n.isEditable.also { if (it) edit = n } }
            edit
        } ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val typed = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (typed && submit) {
            delay(250)
            val enterOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            if (!enterOk) {
                clickWhere { _, l -> l == "search" || l == "go" || l == "enter" || l == "send" || l.contains("search") }
            }
        }
        return typed
    }

    suspend fun scroll(direction: String): Boolean {
        awaitIdle()
        val root = awaitRoot() ?: return false
        var scrollable: AccessibilityNodeInfo? = null
        walk(root) { n -> n.isScrollable.also { if (it) scrollable = n } }
        val up = direction.equals("up", true) || direction.equals("backward", true)
        return scrollable?.performAction(
            if (up) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
        ) ?: false
    }

    /** Compact list of what is on screen: "[tap] Label" / "[input] Label" / "[text] Label". */
    suspend fun readScreen(maxElements: Int = 45, maxLabel: Int = 45): String {
        awaitIdle()
        val root = awaitRoot() ?: return "(no screen content available)"
        val lines = LinkedHashSet<String>()
        walk(root) { n ->
            val text = n.text?.toString()?.trim()
            val desc = n.contentDescription?.toString()?.trim()
            val l = (if (!text.isNullOrEmpty()) text else desc)?.take(maxLabel)
            if (!l.isNullOrEmpty() && !n.isPassword) {
                val tag = when {
                    n.isEditable -> "input"
                    n.isClickable -> "tap"
                    n.isCheckable -> "check"
                    else -> "text"
                }
                lines.add("[$tag] $l")
            }
            lines.size >= maxElements
        }
        return if (lines.isEmpty()) "(no readable elements on screen)" else lines.joinToString("\n")
    }

    /**
     * Flips the first Switch/checkable on the current screen to [on]. [hints] (lowercase words)
     * pick the right row when a page has several toggles (Wi-Fi vs mobile data).
     * Returns "toggled", "already_in_state" or "switch_not_found".
     */
    suspend fun setSwitch(on: Boolean, hints: List<String>): String {
        awaitIdle(quietMs = 300, timeoutMs = 2500)
        val root = awaitRoot() ?: return "switch_not_found"
        val switches = ArrayList<AccessibilityNodeInfo>()
        walk(root) { n ->
            val cls = n.className?.toString().orEmpty()
            if (n.isCheckable || cls.contains("Switch") || cls.contains("Toggle")) switches.add(n)
            false
        }
        if (switches.isEmpty()) return "switch_not_found"

        // Prefer a switch whose own label, or an ancestor's label, mentions a hint.
        fun context(n: AccessibilityNodeInfo): String {
            var cur: AccessibilityNodeInfo? = n
            val sb = StringBuilder()
            var depth = 0
            while (cur != null && depth < 4) {
                sb.append(' ').append(label(cur).lowercase())
                for (i in 0 until cur.childCount) cur.getChild(i)?.let { sb.append(' ').append(label(it).lowercase()) }
                cur = cur.parent
                depth++
            }
            return sb.toString()
        }
        val chosen = switches.firstOrNull { sw -> hints.any { context(sw).contains(it) } } ?: switches.first()
        if (chosen.isChecked == on) return "already_in_state"
        return if (clickNodeOrParent(chosen)) "toggled" else "switch_not_found"
    }

    // ---------------------------------------------------------------------------------------
    // Seeing and touching the screen (the model works from this, step by step)
    // ---------------------------------------------------------------------------------------

    class Element(val label: String, val tag: String, val x: Int, val y: Int, val px: Int, val py: Int, val node: AccessibilityNodeInfo)

    /** The numbered list last returned by [describeScreen]; tap_element(n) refers to it. */
    @Volatile var lastElements: List<Element> = emptyList()
        private set

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    /**
     * Numbered list of everything visible with its centre on a 0..1000 grid (0,0 = top-left,
     * 1000,1000 = bottom-right), e.g. "3. [tap] Search @ (500,120)". Sorted top to bottom.
     */
    suspend fun describeScreen(maxElements: Int = 50): String {
        awaitIdle()
        val root = awaitRoot() ?: return "(no screen content available)"
        val dm = resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(1)
        val h = dm.heightPixels.coerceAtLeast(1)
        val found = ArrayList<Element>()
        val seen = HashSet<String>()
        val r = Rect()
        walk(root) { n ->
            if (n.isVisibleToUser && !n.isPassword) {
                n.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0) {
                    val text = n.text?.toString()?.trim().orEmpty()
                    val desc = n.contentDescription?.toString()?.trim().orEmpty()
                    var label = text.ifEmpty { desc }.take(60)
                    if (label.isEmpty() && n.isEditable) label = (n.hintText?.toString() ?: "text field").take(40)
                    val useful = label.isNotEmpty() || n.isClickable || n.isEditable
                    if (useful) {
                        if (label.isEmpty()) label = n.viewIdResourceName?.substringAfter('/') ?: n.className?.toString()?.substringAfterLast('.').orEmpty()
                        val tag = when {
                            n.isEditable -> "input"
                            n.isCheckable -> if (n.isChecked) "switch:on" else "switch:off"
                            n.isClickable -> "tap"
                            n.isScrollable -> "scroll"
                            else -> "text"
                        }
                        val key = "$label|${r.centerX() / 8}|${r.centerY() / 8}"
                        if (seen.add(key)) found.add(Element(label, tag, r.centerX() * 1000 / w, r.centerY() * 1000 / h, r.centerX(), r.centerY(), n))
                    }
                }
            }
            found.size >= maxElements * 2
        }
        val ordered = found.sortedWith(compareBy({ it.y / 30 }, { it.x })).take(maxElements)
        lastElements = ordered
        if (ordered.isEmpty()) return "(no readable elements on screen)"
        return "app: ${root.packageName}\n" + ordered.mapIndexed { i, e -> "${i + 1}. [${e.tag}] ${e.label} @ (${e.x},${e.y})" }.joinToString("\n")
    }

    /** Taps a pixel with a real touch gesture. */
    suspend fun tapPoint(px: Int, py: Int): Boolean = gesture(Path().apply { moveTo(px.toFloat(), py.toFloat()) }, 60)

    /** Finger swipe in [direction] ("up", "down", "left", "right") across the middle of the screen. */
    suspend fun swipe(direction: String): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val path = Path()
        when (direction.lowercase()) {
            "up" -> { path.moveTo(w / 2, h * 0.72f); path.lineTo(w / 2, h * 0.28f) }
            "down" -> { path.moveTo(w / 2, h * 0.28f); path.lineTo(w / 2, h * 0.72f) }
            "left" -> { path.moveTo(w * 0.8f, h / 2); path.lineTo(w * 0.2f, h / 2) }
            "right" -> { path.moveTo(w * 0.2f, h / 2); path.lineTo(w * 0.8f, h / 2) }
            else -> return false
        }
        return gesture(path, 320)
    }

    private suspend fun gesture(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build()
        val started = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!started && cont.isActive) cont.resume(false)
    }

    /** Taps element [number] from the last [describeScreen] list: native click first, touch gesture as fallback. */
    suspend fun tapElement(number: Int): Boolean {
        val e = lastElements.getOrNull(number - 1) ?: return false
        if (clickNodeOrParent(e.node)) return true
        return tapPoint(e.px, e.py)
    }

    /** A small JPEG of the screen (Android 11+), or null when the system refuses (e.g. protected windows). */
    suspend fun screenshotJpeg(maxEdge: Int = 768, quality: Int = 55): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val shot = suspendCancellableCoroutine<ScreenshotResult?> { cont ->
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) { if (cont.isActive) cont.resume(result) }
                override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
            })
        } ?: return null
        return try {
            val hw = Bitmap.wrapHardwareBuffer(shot.hardwareBuffer, shot.colorSpace) ?: return null
            val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
            hw.recycle()
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(soft.width, soft.height))
            val sized = if (scale < 1f) Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt(), (soft.height * scale).toInt(), true) else soft
            val out = java.io.ByteArrayOutputStream()
            sized.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (e: Exception) {
            null
        } finally {
            shot.hardwareBuffer.close()
        }
    }

    fun hasAdOrSponsoredOnScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        val adKeywords = listOf("sponsored", "ad", "promoted", "paid partnership", "विज्ञापन", "प्रायोजित", "suggested for you", "suggested post", "install now", "shop now", "sign up")

        fun checkNode(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (adKeywords.any { text == it || text.contains(it) || desc.contains(it) }) return true
            for (i in 0 until node.childCount) {
                if (checkNode(node.getChild(i))) return true
            }
            return false
        }
        return checkNode(root)
    }

    override fun onInterrupt() {
        Log.d(TAG, "SERVICE INTERRUPTED")
    }

    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(43)
        } else false
    }

    override fun onDestroy() {
        super.onDestroy()

        instance = null
        currentRoot = null
        SmartAccessibilityEngine.service = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}

        Log.d(TAG, "SERVICE DESTROYED")
    }
}