package dev.voxpilot.assistant.automation

import dev.voxpilot.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AutoScrollManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scrollJob: Job? = null
    var isRunning: Boolean = false
        private set

    fun start(intervalSeconds: Int = 15) {
        stop()
        isRunning = true
        val targetMs = intervalSeconds.coerceIn(5, 60) * 1000L
        scrollJob = scope.launch {
            var elapsed = 0L
            while (isActive && isRunning) {
                delay(500L) // check every 0.5s for instant ad skipping
                elapsed += 500L

                val a11y = AccessibilityHelperService.instance
                if (a11y != null) {
                    // Check if ad/sponsored post is present -> skip immediately
                    if (a11y.hasAdOrSponsoredOnScreen()) {
                        a11y.swipe("up")
                        elapsed = 0L
                        delay(1200L)
                        continue
                    }
                }

                // Normal reel duration reached -> swipe up
                if (elapsed >= targetMs) {
                    a11y?.swipe("up")
                    elapsed = 0L
                    delay(1200L)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        scrollJob?.cancel()
        scrollJob = null
    }
}
