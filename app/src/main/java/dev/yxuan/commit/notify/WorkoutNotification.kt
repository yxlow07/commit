package dev.yxuan.commit.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.R
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.notify.NotificationActionReceiver.Companion.ACTION_WORKOUT_ADD_REST
import dev.yxuan.commit.notify.NotificationActionReceiver.Companion.ACTION_WORKOUT_DONE_SET
import dev.yxuan.commit.notify.NotificationActionReceiver.Companion.ACTION_WORKOUT_SKIP_REST
import dev.yxuan.commit.ui.format.WorkoutFormat

/**
 * Ongoing workout notification (F5.7): countdown plus "Done set", "+30s" and "Skip rest",
 * usable from the lock screen. Silent; the separate alert notification makes the sound (F5.6).
 */
object WorkoutNotification {
    private const val ONGOING_ID = 0x0EC0
    private const val ALERT_ID = 0x0EC1

    fun update(context: Context, live: LiveWorkout?) {
        if (live == null) {
            Notifications.cancel(context, ONGOING_ID)
            Notifications.cancel(context, ALERT_ID)
            return
        }
        val s = live.state
        val ex = live.exercise
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_WORKOUT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(context))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(s.phase != WorkoutPhase.COMPLETE)
            // Spec: the one dark notification. Android colourises only foreground-service and media
            // notifications, so on a plain ongoing one this tints the icon and actions.
            .setColor(0xFF1F2123.toInt())
            .setColorized(true)

        when (s.phase) {
            WorkoutPhase.READY -> builder
                .setContentTitle(ex?.name ?: live.routine.name)
                .setContentText(WorkoutFormat.setLine(live))
                .addAction(0, if (live.isTimed) "Start set" else "Done set", NotificationActionReceiver.workout(context, ACTION_WORKOUT_DONE_SET))
            WorkoutPhase.REST -> if (live.restOver(System.currentTimeMillis())) builder
                // Overtime: counts up from the end of the rest until "Next set".
                .setContentTitle("Rest over")
                .setContentText(WorkoutFormat.nextLine(live) ?: "")
                .setWhen(s.phaseEndsAt ?: System.currentTimeMillis()).setShowWhen(true).setUsesChronometer(true)
                .addAction(0, "Next set", NotificationActionReceiver.workout(context, ACTION_WORKOUT_SKIP_REST))
            else builder
                .setContentTitle("Resting")
                .setContentText(WorkoutFormat.nextLine(live) ?: "")
                .countdownTo(s.phaseEndsAt)
                .addAction(0, "Add 30 s", NotificationActionReceiver.workout(context, ACTION_WORKOUT_ADD_REST))
                .addAction(0, "Skip rest", NotificationActionReceiver.workout(context, ACTION_WORKOUT_SKIP_REST))
            WorkoutPhase.WORK -> builder
                .setContentTitle(ex?.name ?: "Working")
                .setContentText(WorkoutFormat.setLine(live))
                .countdownTo(s.phaseEndsAt)
                .addAction(0, "Done early", NotificationActionReceiver.workout(context, ACTION_WORKOUT_SKIP_REST))
            WorkoutPhase.COMPLETE -> builder
                .setContentTitle("Workout done")
                .setContentText(WorkoutFormat.summary(live))
                .setAutoCancel(true)
        }
        Notifications.post(context, ONGOING_ID, builder.build())
    }

    /** Sound and vibration when a timer ends and the workout moves on (F5.6). */
    fun alert(context: Context, live: LiveWorkout) {
        val s = live.state
        val text = when (s.phase) {
            WorkoutPhase.READY -> "Go: ${live.exercise?.name}, set ${s.setNo}"
            WorkoutPhase.WORK -> "Go: ${live.exercise?.name}, ${live.exercise?.durationSeconds} s"
            WorkoutPhase.REST -> if (live.restOver(System.currentTimeMillis())) "Rest over. " + (WorkoutFormat.nextLine(live) ?: "")
                else "Resting. " + (WorkoutFormat.nextLine(live) ?: "")
            WorkoutPhase.COMPLETE -> "Workout done, ${WorkoutFormat.summary(live)}"
        }
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_WORKOUT_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(live.routine.name)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        Notifications.post(context, ALERT_ID, notification)
    }

    private fun NotificationCompat.Builder.countdownTo(endsAt: Long?): NotificationCompat.Builder {
        if (endsAt == null) return this
        return setWhen(endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        ONGOING_ID,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
