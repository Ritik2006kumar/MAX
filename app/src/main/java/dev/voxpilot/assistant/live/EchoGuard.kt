package dev.voxpilot.assistant.live

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single source of truth for "is it safe for the microphone to be live right now".
 * VoxPilot's own voice comes out of the loudspeaker, so without this the mic hears her and the
 * Live API answers itself in a loop. Playback calls [notifyPlaybackStarted]/[notifyPlaybackStopped];
 * the mic loop checks [isMicMuted] before forwarding a chunk. The mic stays muted for
 * [ECHO_TAIL_MS] after playback ends, because room reverb outlasts the audio track.
 */
object EchoGuard {
    private const val TAG = "EchoGuard"
    const val ECHO_TAIL_MS = 350L
    private const val SAFETY_CEILING_MS = 60_000L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activePlaybacks = AtomicInteger(0)
    private val muted = AtomicBoolean(false)
    private var tailJob: Job? = null
    private var safetyJob: Job? = null

    @Synchronized
    fun notifyPlaybackStarted() {
        tailJob?.cancel()
        tailJob = null
        val wasIdle = activePlaybacks.getAndIncrement() == 0
        if (wasIdle) {
            safetyJob?.cancel()
            safetyJob = scope.launch {
                delay(SAFETY_CEILING_MS)
                Log.w(TAG, "Safety watchdog fired - forcing unmute")
                reset()
            }
        }
        muted.set(true)
    }

    @Synchronized
    fun notifyPlaybackStopped() {
        val remaining = activePlaybacks.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (remaining > 0) return
        safetyJob?.cancel()
        safetyJob = null
        tailJob?.cancel()
        tailJob = scope.launch {
            delay(ECHO_TAIL_MS)
            muted.set(false)
        }
    }

    fun isMicMuted(): Boolean = muted.get()

    @Synchronized
    fun reset() {
        tailJob?.cancel()
        tailJob = null
        safetyJob?.cancel()
        safetyJob = null
        activePlaybacks.set(0)
        muted.set(false)
    }
}
