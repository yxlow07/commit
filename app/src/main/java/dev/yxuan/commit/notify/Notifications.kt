package dev.yxuan.commit.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.R
import dev.yxuan.commit.data.db.entity.TodoEntity

/** Notification channels (F7.5) and the todo reminder notification. */
object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_WORKOUT = "workout"
    const val CHANNEL_WORKOUT_ALERTS = "workout_alerts"
    const val CHANNEL_RECAPS = "recaps"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Todos due now and recurring chores due today"
                },
                NotificationChannel(CHANNEL_WORKOUT, "Workout", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing workout with the rest timer and controls"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_WORKOUT_ALERTS, "Workout timer alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Sound and vibration when a rest or work timer ends"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_RECAPS, "Daily recaps", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Morning and night summaries"
                },
            )
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canPost
    fun post(context: Context, id: Int, notification: Notification) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun todoNotificationId(todoId: String): Int = todoId.hashCode()

    /** Reminder for a todo, with tap-to-open and a "Done" action (F7.1, F7.4). */
    fun todoReminder(context: Context, todo: TodoEntity, text: String): Notification {
        val open = PendingIntent.getActivity(
            context,
            todo.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TODO_ID, todo.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(todo.title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Done", NotificationActionReceiver.todoDone(context, todo.id))
            .build()
    }
}
