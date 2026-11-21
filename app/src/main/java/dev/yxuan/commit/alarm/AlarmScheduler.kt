package dev.yxuan.commit.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.SettingsEntity
import dev.yxuan.commit.domain.Recurrence
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/** What an alarm is for. The id part of the alarm URI is a todo id, or a fixed name. */
enum class AlarmKind { TODO_DUE, CHORE_DUE, MIDNIGHT, WORKOUT_TIMER, MORNING_RECAP, NIGHT_RECAP }

/**
 * Schedules every time-based alarm. Alarms only notify and redraw; the state they reveal is
 * always recalculated from timestamps, so a late or missed alarm never shows wrong state.
 */
class AlarmScheduler(
    private val context: Context,
    private val db: AppDatabase,
    private val clock: Clock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Cancels and re-sets due reminders and chore alarms for every todo (PRD "Alarm scheduling"). */
    suspend fun rescheduleTodos() {
        val now = clock.millis()
        val today = LocalDate.now(clock)
        val choreTime = LocalTime.ofSecondOfDay(
            ((db.settingsDao().get()?.morningRecapTime ?: DEFAULT_MORNING) * 60L).coerceIn(0, 86_399)
        )
        for (todo in db.todoDao().getAll()) {
            cancel(AlarmKind.TODO_DUE, todo.id)
            cancel(AlarmKind.CHORE_DUE, todo.id)
            if (todo.deleted) continue
            if (Recurrence.isRecurring(todo)) {
                // Chore resets at the start of its due day; remind at the morning time (F4.4, F7.3).
                val next = Recurrence.nextDueDate(todo, clock.zone, today) ?: continue
                val at = next.atTime(choreTime).atZone(clock.zone).toInstant().toEpochMilli()
                if (at > now) setExact(AlarmKind.CHORE_DUE, todo.id, at)
            } else if (todo.hasTime && todo.doneAt == null && todo.dueAt != null && todo.dueAt > now) {
                setExact(AlarmKind.TODO_DUE, todo.id, todo.dueAt) // F7.1; date-only todos go to the morning recap
            }
        }
    }

    /**
     * Next morning and night recaps at the times in settings (F6.1, F6.2). Re-armed after each one
     * fires, on settings change, boot and time-zone change.
     */
    suspend fun scheduleRecaps() {
        val settings = db.settingsDao().get() ?: SettingsEntity(id = "", updatedAt = 0)
        scheduleRecap(AlarmKind.MORNING_RECAP, settings.morningRecapEnabled, settings.morningRecapTime)
        scheduleRecap(AlarmKind.NIGHT_RECAP, settings.nightRecapEnabled, settings.nightRecapTime)
    }

    private fun scheduleRecap(kind: AlarmKind, enabled: Boolean, minuteOfDay: Int) {
        cancel(kind, "recap")
        if (!enabled) return
        val time = LocalTime.ofSecondOfDay((minuteOfDay * 60L).coerceIn(0, 86_399))
        val now = java.time.ZonedDateTime.now(clock)
        var next = now.toLocalDate().atTime(time).atZone(clock.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        setExact(kind, "recap", next.toInstant().toEpochMilli())
    }

    /** Daily redraw just after local midnight, so "Today"/"Overdue" labels and chores stay correct. */
    fun scheduleMidnight() {
        val at = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() + 5_000
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000, pending(AlarmKind.MIDNIGHT, "midnight"))
    }

    /** Workout rest/work timer end. `setAlarmClock` is the most reliable in Doze (NF: within 2 s). */
    fun setWorkoutTimer(at: Long) {
        val pi = pending(AlarmKind.WORKOUT_TIMER, "workout")
        if (alarms.canScheduleExactAlarms()) {
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10_000, pi)
        }
    }

    fun cancelWorkoutTimer() = cancel(AlarmKind.WORKOUT_TIMER, "workout")

    val canScheduleExact: Boolean get() = alarms.canScheduleExactAlarms()

    private fun setExact(kind: AlarmKind, id: String, at: Long) {
        val pi = pending(kind, id)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi) // fallback: within 10 min
        }
    }

    private fun cancel(kind: AlarmKind, id: String) {
        val pi = PendingIntent.getBroadcast(context, 0, intent(kind, id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            alarms.cancel(pi)
            pi.cancel()
        }
    }

    private fun pending(kind: AlarmKind, id: String): PendingIntent =
        PendingIntent.getBroadcast(context, 0, intent(kind, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    // The data URI makes each (kind, id) a distinct PendingIntent.
    private fun intent(kind: AlarmKind, id: String) = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_ALARM)
        .setData(Uri.parse("commit://alarm/${kind.name}/$id"))

    companion object {
        const val ACTION_ALARM = "dev.yxuan.commit.action.ALARM"
        private val DEFAULT_MORNING = SettingsEntity(id = "", updatedAt = 0).morningRecapTime

        fun parse(intent: Intent): Pair<AlarmKind, String>? {
            val segments = intent.data?.pathSegments ?: return null
            // URI is commit://alarm/<KIND>/<id>: host = "alarm", path = /<KIND>/<id>
            if (segments.size != 2) return null
            val kind = runCatching { AlarmKind.valueOf(segments[0]) }.getOrNull() ?: return null
            return kind to segments[1]
        }
    }
}
