package dev.yxuan.commit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.yxuan.commit.appContainer

/** Tab tap (F3.1): stores the selected commitment for this widget instance and redraws it. */
class SelectTabAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val commitmentId = parameters[CommitmentIdParam] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[SelectedCommitmentKey] = commitmentId
        }
        CommitWidget().update(context, glanceId)
    }
}

/**
 * Checkbox tap (F3.3): marks the todo done in Room. The repository's change listener redraws
 * every widget, and the row drops out because the widget only lists undone todos.
 */
class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[TodoIdParam] ?: return
        context.appContainer.todoRepository.setDone(todoId, done = true)
    }
}
