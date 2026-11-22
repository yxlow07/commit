package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.TodoEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recurring-todo rules (F4). "Done" is calculated, never stored (F4.4):
 * - Interval mode (F4.2): chore done on day D with interval N is due again at the start of day D + N.
 * - Calendar mode (F4.3): chore done on day D with weekday mask M is due again on the next matching day after D.
 * A chore is "done" while today is before its next due date.
 */
object Recurrence {
    fun isRecurring(todo: TodoEntity): Boolean =
        (todo.recurIntervalDays ?: 0) > 0 || (todo.recurDays ?: 0) > 0

    /** "Every day" / "Every 4 days". */
    fun intervalLabel(days: Int): String = if (days == 1) "Every day" else "Every $days days"

    /** Label for interval ("Every 4 days") or calendar ("Mon, Thu") recurrence. */
    fun label(todo: TodoEntity): String {
        val days = todo.recurDays ?: 0
        if (days > 0) return Weekdays.label(days)
        val interval = todo.recurIntervalDays ?: 0
        if (interval > 0) return intervalLabel(interval)
        return ""
    }

    /** Next due date of a recurring todo; the due date of a one-off todo. */
    fun nextDueDate(todo: TodoEntity, zone: ZoneId, today: LocalDate): LocalDate? {
        if (!isRecurring(todo)) return todo.dueAt?.toLocalDate(zone)
        val recurDays = todo.recurDays ?: 0
        if (recurDays > 0) {
            val last = todo.lastDoneAt
            // Never done: the first matching day on or after the due date (or today). A missed day
            // stays as the due date, so the chore shows as overdue until done (same as the web).
            if (last == null) return nextMatchingDay(recurDays, todo.dueAt?.toLocalDate(zone) ?: today)
            val strictlyAfter = last.toLocalDate(zone).plusDays(1)
            return nextMatchingDay(recurDays, strictlyAfter)
        }
        val last = todo.lastDoneAt
            ?: return todo.dueAt?.toLocalDate(zone) ?: today
        return last.toLocalDate(zone).plusDays(todo.recurIntervalDays!!.toLong())
    }

    /** Finds the earliest date on or after [fromDate] that matches the weekday [mask]. */
    fun nextMatchingDay(mask: Int, fromDate: LocalDate): LocalDate {
        var cur = fromDate
        for (i in 0..7) {
            if (Weekdays.isPlanned(mask, cur)) return cur
            cur = cur.plusDays(1)
        }
        return fromDate
    }

    fun isDone(todo: TodoEntity, clock: Clock): Boolean {
        if (!isRecurring(todo)) return todo.doneAt != null
        if (todo.lastDoneAt == null) return false
        val today = LocalDate.now(clock)
        val next = nextDueDate(todo, clock.zone, today) ?: return false
        return today.isBefore(next)
    }

    /** Due instant and whether it has a time, for sorting and labels (F3.5). */
    fun effectiveDue(todo: TodoEntity, clock: Clock): Pair<Long?, Boolean> {
        if (!isRecurring(todo)) return todo.dueAt to todo.hasTime
        val next = nextDueDate(todo, clock.zone, LocalDate.now(clock))!!
        // Never done and due on its own due date: keep the due time ("Today 18:00").
        if (todo.lastDoneAt == null && todo.dueAt != null && next == todo.dueAt.toLocalDate(clock.zone)) {
            return todo.dueAt to todo.hasTime
        }
        return next.atStartOfDay(clock.zone).toInstant().toEpochMilli() to false
    }

    private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}

/** A todo with its calculated state, as shown in lists and on the widget. */
data class TodoItem(
    val todo: TodoEntity,
    val dueAt: Long?,
    val hasTime: Boolean,
    val isDone: Boolean,
) {
    val isRecurring: Boolean get() = Recurrence.isRecurring(todo)

    companion object {
        fun of(todo: TodoEntity, clock: Clock): TodoItem {
            val (dueAt, hasTime) = Recurrence.effectiveDue(todo, clock)
            return TodoItem(todo, dueAt, hasTime, Recurrence.isDone(todo, clock))
        }

        /** Undone items in widget order: by due date, undated last, then title (F3.2). */
        fun open(todos: List<TodoEntity>, clock: Clock): List<TodoItem> =
            todos.map { of(it, clock) }.filterNot { it.isDone }.sortedWith(ORDER)

        val ORDER: Comparator<TodoItem> = compareBy<TodoItem> { it.dueAt == null }
            .thenBy { it.dueAt }
            .thenBy { it.todo.title.lowercase() }
    }
}
