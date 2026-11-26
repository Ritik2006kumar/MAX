package dev.voxpilot.assistant.live

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.sqrt

/** Mic capture (16 kHz) + speaker playback (24 kHz) for the Gemini Live API. */
class LiveAudioIO(
    private val onMicChunk: (ByteArray) -> Unit,
    private val onMicAmplitude: (Float) -> Unit,
) {
    private var recordThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var isRecording = false
    @Volatile private var volume: Float = 1f
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    @SuppressLint("MissingPermission")
    fun startMic() {
        if (isRecording) return
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 4096),
        )
        audioRecord = record
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(record.audioSessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio effect setup failed: ${e.message}")
        }
        record.startRecording()
        isRecording = true

        recordThread = thread(name = "mic-capture") {
            // 1024 bytes = 32 ms of 16 kHz PCM16: small chunks get the first words to Gemini sooner.
            val chunk = ByteArray(1024)
            while (isRecording) {
                val read = record.read(chunk, 0, chunk.size)
                if (read > 0) {
                    val exact = chunk.copyOf(read)
                    onMicChunk(exact)
                    onMicAmplitude(rmsOf(exact))
                }
            }
        }
    }

    fun stopMic() {
        isRecording = false
        recordThread?.join(500)
        recordThread = null
        echoCanceler?.release(); echoCanceler = null
        noiseSuppressor?.release(); noiseSuppressor = null
        gainControl?.release(); gainControl = null
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioRecord = null
    }

    private fun ensurePlaybackStarted() {
        if (audioTrack != null) return
        val sampleRate = 24000
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // USAGE_ASSISTANT = full-quality media path; USAGE_VOICE_COMMUNICATION applies
                    // telephony DSP on many phones and makes replies sound slow and dull.
                    .setUsage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.setVolume(volume)
        audioTrack?.play()
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        audioTrack?.setVolume(volume)
    }

    /** Plays one chunk of 24 kHz PCM16 assistant audio; returns its RMS level. */
    fun playChunk(pcm16: ByteArray): Float {
        ensurePlaybackStarted()
        audioTrack?.write(pcm16, 0, pcm16.size)
        return rmsOf(pcm16)
    }

    /** Drops whatever is still queued (barge-in). */
    fun flushPlayback() {
        audioTrack?.apply {
            pause()
            flush()
            play()
        }
    }

    fun release() {
        stopMic()
        audioTrack?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioTrack = null
    }

    companion object {
        private const val TAG = "LiveAudioIO"

        fun rmsOf(pcm16: ByteArray): Float {
            if (pcm16.size < 2) return 0f
            var sum = 0.0
            var samples = 0
            var i = 0
            while (i + 1 < pcm16.size) {
                val sample = ((pcm16[i + 1].toInt() shl 8) or (pcm16[i].toInt() and 0xFF)).toShort()
                sum += (sample * sample).toDouble()
                samples++
                i += 2
            }
            if (samples == 0) return 0f
            return (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
        }
    }
}
