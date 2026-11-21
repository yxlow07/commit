package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.TodoEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Recurring-todo rules (F4). "Done" is calculated, never stored (F4.4):
 * a chore done on day D with interval N is due again at the start of day D + N (local time),
 * so it is "done" while today is before that date.
 */
object Recurrence {
    fun isRecurring(todo: TodoEntity): Boolean = (todo.recurIntervalDays ?: 0) > 0

    /** "every day" / "every 4 days". */
    fun intervalLabel(days: Int): String = if (days == 1) "every day" else "every $days days"

    /** Next due date of a recurring todo; the due date of a one-off todo. */
    fun nextDueDate(todo: TodoEntity, zone: ZoneId, today: LocalDate): LocalDate? {
        if (!isRecurring(todo)) return todo.dueAt?.toLocalDate(zone)
        val last = todo.lastDoneAt
            ?: return todo.dueAt?.toLocalDate(zone) ?: today
        return last.toLocalDate(zone).plusDays(todo.recurIntervalDays!!.toLong())
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
        if (!isRecurring(todo) || todo.lastDoneAt == null && todo.dueAt != null) {
            return todo.dueAt to todo.hasTime
        }
        val next = nextDueDate(todo, clock.zone, LocalDate.now(clock))!!
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
