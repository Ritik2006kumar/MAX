package dev.voxpilot.assistant.voice

import dev.voxpilot.assistant.utils.AudioUtils
import dev.voxpilot.assistant.websocket.GeminiWebSocketClient

/**
 * Bridges AudioRecorder chunks → GeminiWebSocketClient
 */
class AudioStreamSender(private val client: GeminiWebSocketClient) {

    fun send(pcm: ByteArray) {
        if (VoiceStateManager.isAiSpeaking()) return
        client.sendAudioChunk(pcm)
    }
}
