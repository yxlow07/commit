package dev.yxuan.commit.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.yxuan.commit.alarm.launchAsync

/** Buttons on notifications: "Done" on todo reminders (F7.4) and the workout controls (F5.7). */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        launchAsync(context) {
            when (action) {
                ACTION_TODO_DONE -> {
                    val todoId = intent.data?.lastPathSegment ?: return@launchAsync
                    todoRepository.setDone(todoId, true)
                    Notifications.cancel(context, Notifications.todoNotificationId(todoId))
                }
                else -> onWorkoutAction(action)
            }
        }
    }

    companion object {
        const val ACTION_TODO_DONE = "dev.yxuan.commit.action.TODO_DONE"
        const val ACTION_WORKOUT_DONE_SET = "dev.yxuan.commit.action.WORKOUT_DONE_SET"
        const val ACTION_WORKOUT_ADD_REST = "dev.yxuan.commit.action.WORKOUT_ADD_REST"
        const val ACTION_WORKOUT_SKIP_REST = "dev.yxuan.commit.action.WORKOUT_SKIP_REST"

        fun todoDone(context: Context, todoId: String): PendingIntent = broadcast(
            context,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(ACTION_TODO_DONE)
                .setData(Uri.parse("commit://todo/$todoId")),
        )

        fun workout(context: Context, action: String): PendingIntent =
            broadcast(context, Intent(context, NotificationActionReceiver::class.java).setAction(action))

        private fun broadcast(context: Context, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
