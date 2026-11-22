package dev.yxuan.commit.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.yxuan.commit.AppContainer
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.domain.RecapKind
import dev.yxuan.commit.domain.ReminderText
import dev.yxuan.commit.domain.Recurrence
import dev.yxuan.commit.domain.TodoItem
import dev.yxuan.commit.notify.Notifications
import dev.yxuan.commit.notify.RecapNotification
import kotlinx.coroutines.launch

/** Runs suspending work from a receiver, keeping the process alive until it finishes. */
fun BroadcastReceiver.launchAsync(context: Context, block: suspend AppContainer.() -> Unit) {
    val pending = goAsync()
    val container = context.appContainer
    container.appScope.launch {
        try {
            container.block()
        } finally {
            pending.finish()
        }
    }
}

/** Handles every alarm set by [AlarmScheduler]. Works with the app closed. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (kind, id) = AlarmScheduler.parse(intent) ?: return
        launchAsync(context) {
            when (kind) {
                AlarmKind.TODO_DUE -> {
                    alarmScheduler.clearSnooze(id)
                    val todo = todoRepository.get(id)
                    if (todo != null && !todo.deleted && todo.doneAt == null && todo.dueAt != null) {
                        // Early with a lead time, on time, or late after a snooze: "Due 18:00, Study".
                        val commitment = database.commitmentDao().getById(todo.commitmentId)?.name
                        val label = ReminderText.label(todo.dueAt, clock.millis(), clock.zone, commitment)
                        Notifications.post(context, Notifications.todoNotificationId(id), Notifications.todoReminder(context, todo, label))
                    }
                }
                AlarmKind.CHORE_DUE -> {
                    val todo = todoRepository.get(id)
                    if (todo != null && !todo.deleted && Recurrence.isRecurring(todo) && !TodoItem.of(todo, clock).isDone) {
                        val commitment = database.commitmentDao().getById(todo.commitmentId)?.name
                        val text = listOfNotNull("Due today", Recurrence.label(todo).let { if (it.startsWith("Every")) it.replaceFirstChar(Char::lowercase) else it }, commitment).joinToString(", ")
                        Notifications.post(context, Notifications.todoNotificationId(id), Notifications.todoReminder(context, todo, text))
                    }
                }
                AlarmKind.MIDNIGHT -> alarmScheduler.scheduleMidnight()
                AlarmKind.MORNING_RECAP, AlarmKind.NIGHT_RECAP -> {
                    val recapKind = if (kind == AlarmKind.MORNING_RECAP) RecapKind.MORNING else RecapKind.NIGHT
                    // Built from Room now, so it reflects the latest sync (F6.3); skipped when empty (F6.5).
                    val recap = recapRepository.build(recapKind)
                    if (!recap.isEmpty) RecapNotification.post(context, recap)
                    alarmScheduler.scheduleRecaps()
                }
                AlarmKind.WORKOUT_TIMER -> onWorkoutTimer()
            }
            widgetUpdater.refresh()
        }
    }
}

/** Re-registers alarms after reboot, app update, time or time-zone change (PRD "Reliability"). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        launchAsync(context) {
            alarmScheduler.rescheduleTodos()
            alarmScheduler.scheduleMidnight()
            alarmScheduler.scheduleRecaps()
            onWorkoutTimer()
            widgetUpdater.refresh()
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
