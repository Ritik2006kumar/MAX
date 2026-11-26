package dev.voxpilot.assistant.automation

import dev.voxpilot.assistant.accessibility.DynamicClickEngine
import dev.voxpilot.assistant.accessibility.ScrollController
import dev.voxpilot.assistant.accessibility.TypingController
import dev.voxpilot.assistant.models.ActionModel
import dev.voxpilot.assistant.models.ActionType
import dev.voxpilot.assistant.utils.Logger

object TaskExecutor {
    private val TAG = "TASK_EXEC"

    fun execute(action: ActionModel): Boolean {
        Logger.d(TAG, "Executing: ${action.type} target=${action.target}")
        return when (action.type) {
            ActionType.CLICK_TEXT -> DynamicClickEngine.clickByText(action.target)
            ActionType.TYPE_TEXT -> TypingController.typeText(action.value)
            ActionType.SCROLL_DOWN -> ScrollController.scrollDown()
            ActionType.SCROLL_UP -> ScrollController.scrollUp()
            else -> {
                Logger.w(TAG, "Unhandled action: ${action.type}")
                false
            }
        }
    }
}
