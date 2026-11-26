package dev.voxpilot.assistant.live

import dev.voxpilot.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * While VoxPilot is working the phone, a screenshot goes to the model about once a second so it sees
 * what is really on screen. Every screen tool call extends the window; it stops by itself when
 * nothing happens for [WINDOW_MS]. Private apps (banking, passwords) are never captured.
 */
object ScreenFeed {
    private const val WINDOW_MS = 15_000L
    private const val INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var until = 0L

    fun touch() {
        until = System.currentTimeMillis() + WINDOW_MS
        if (job?.isActive == true) return
        job = scope.launch {
            while (System.currentTimeMillis() < until && LiveSession.isActive) {
                val svc = AccessibilityHelperService.instance ?: break
                if (!DeviceActions.isPrivateForeground(svc)) {
                    svc.screenshotJpeg()?.let { LiveSession.sendFrame(it) }
                }
                delay(INTERVAL_MS)
            }
        }
    }
}
