package dev.yxuan.commit.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.R
import dev.yxuan.commit.domain.Recap
import dev.yxuan.commit.domain.RecapKind

/** Morning / night recap notification: expandable inbox list, tap opens the recap screen (F6.4). */
object RecapNotification {
    private const val MAX_LINES = 6

    fun post(context: Context, recap: Recap) {
        val style = NotificationCompat.InboxStyle().setBigContentTitle(recap.title)
        val lines = recap.lines
        lines.take(MAX_LINES).forEach(style::addLine)
        if (lines.size > MAX_LINES) style.setSummaryText("+${lines.size - MAX_LINES} more")

        val open = PendingIntent.getActivity(
            context,
            recap.kind.ordinal + 100,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_RECAP, recap.kind.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_RECAPS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(recap.title)
            .setContentText(recap.summary)
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        Notifications.post(context, id(recap.kind), notification)
    }

    private fun id(kind: RecapKind) = if (kind == RecapKind.MORNING) 0x0E70 else 0x0E71
}
