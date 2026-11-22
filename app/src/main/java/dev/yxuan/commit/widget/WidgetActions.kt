package dev.yxuan.commit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.yxuan.commit.appContainer

/**
 * Tab tap (F3.1): stores the selected commitment for this widget instance and redraws it. Rows
 * ticked on the previous tab are forgotten, so they disappear (spec: "until leaving the tab").
 */
class SelectTabAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val commitmentId = parameters[CommitmentIdParam] ?: return
        selectTab(context, glanceId, commitmentId)
    }
}

suspend fun selectTab(context: Context, glanceId: GlanceId, commitmentId: String) {
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[SelectedCommitmentKey] = commitmentId
        prefs.remove(RecentlyDoneKey)
    }
    CommitWidget().update(context, glanceId)
}

/**
 * Checkbox tap (F3.3): marks the todo done or not done in Room. A ticked row stays visible, checked,
 * until the tab changes; unticking before then restores it. The repository's change listener redraws
 * every widget and schedules a sync.
 */
class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val todoId = parameters[TodoIdParam] ?: return
        val done = parameters[DoneParam] ?: true
        val row = parameters[RowParam] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            // Entries are "id|row", so the checked row is drawn where it was.
            val others = prefs[RecentlyDoneKey].orEmpty().filterNot { it.substringBefore('|') == todoId }.toSet()
            prefs[RecentlyDoneKey] = if (done) others + "$todoId|$row" else others
        }
        context.appContainer.todoRepository.setDone(todoId, done = done)
        CommitWidget().update(context, glanceId)
    }
}
