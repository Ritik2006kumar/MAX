package dev.voxpilot.assistant.accessibility

object PendingReplyManager {
    var lastSender: String? = null
    var lastMessage: String? = null
    var pendingApp: String = "whatsapp"

    fun setPending(sender: String, message: String, app: String = "whatsapp") {
        lastSender = sender
        lastMessage = message
        pendingApp = app
    }

    fun clear() {
        lastSender = null
        lastMessage = null
    }
}
