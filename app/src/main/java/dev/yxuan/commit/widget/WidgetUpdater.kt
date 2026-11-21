package dev.yxuan.commit.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

/** Redraws every placed widget instance; called after each local data change. */
class WidgetUpdater(private val context: Context) {
    suspend fun refresh() {
        CommitWidget().updateAll(context)
    }
}

class CommitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CommitWidget()
}
