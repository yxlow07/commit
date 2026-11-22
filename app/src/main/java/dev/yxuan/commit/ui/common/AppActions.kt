package dev.yxuan.commit.ui.common

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.yxuan.commit.notify.Notifications
import dev.yxuan.commit.quickadd.QuickAddActivity
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.widget.CommitmentIdParam

/** Opens the quick-add popup (the same one the widget uses), optionally preselecting a commitment. */
fun openQuickAdd(context: Context, commitmentId: String?) {
    context.startActivity(
        Intent(context, QuickAddActivity::class.java).apply {
            if (commitmentId != null) putExtra(CommitmentIdParam.name, commitmentId)
        }
    )
}

/** Shown while notifications are off: reminders, chores and timers can't alert (F7). */
@Composable
fun NotificationsOffNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Notifications.canPost(context)
        onPauseOrDispose {}
    }
    if (allowed) return
    Column(modifier) {
        Text("Notifications are off, so reminders and timers can't alert you.", style = Commit.type.meta, color = Commit.colors.textStrong)
        TextAction(
            "Turn on notifications",
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
