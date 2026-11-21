package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.SessionSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.ui.format.DueFormatter
import dev.yxuan.commit.ui.format.DueStatus
import dev.yxuan.commit.ui.format.WorkoutFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

enum class RecapKind { MORNING, NIGHT }

data class RecapSection(val title: String, val lines: List<String>)

/** A daily recap (F6). [summary] is the one-line text of the collapsed notification. */
data class Recap(
    val kind: RecapKind,
    val title: String,
    val summary: String,
    val sections: List<RecapSection>,
) {
    val isEmpty: Boolean get() = sections.all { it.lines.isEmpty() }
    val lines: List<String> get() = sections.flatMap { s -> s.lines }
}

/** Everything a recap is built from; loaded from Room at the moment the recap fires (F6.3). */
data class RecapInput(
    /** Not-completed todos of active commitments (one-off undone + all recurring). */
    val openTodos: List<TodoEntity>,
    val commitments: List<CommitmentEntity>,
    val routines: List<RoutineEntity>,
    /** Completions done today. */
    val completedToday: List<CompletionRow>,
    /** Workout sessions that ended today. */
    val sessionsToday: List<SessionSummary>,
)

/**
 * Builds the morning (F6.1) and night (F6.2) recaps. Pure: no Android, no database, so the
 * same code backs the notification, the recap screen and the unit tests.
 */
class RecapBuilder(private val clock: Clock, private val locale: Locale = Locale.getDefault()) {

    fun build(kind: RecapKind, input: RecapInput): Recap = when (kind) {
        RecapKind.MORNING -> morning(input)
        RecapKind.NIGHT -> night(input)
    }

    private fun morning(input: RecapInput): Recap {
        val today = LocalDate.now(clock)
        val items = openItems(input)
        val overdue = items.filter { label(it).status == DueStatus.OVERDUE }
        val dueToday = items.filter { label(it).status == DueStatus.TODAY }
        val todos = dueToday.filterNot { it.isRecurring }
        val chores = dueToday.filter { it.isRecurring }
        val planned = input.routines.filter { Weekdays.isPlanned(it.plannedDays, today) }

        val sections = listOf(
            RecapSection("Overdue", overdue.map { line(it, input) }),
            RecapSection("Due today", todos.map { line(it, input) }),
            RecapSection("Chores", chores.map { "${it.todo.title} · chore" }),
            RecapSection("Workout", planned.map { "Today: ${it.name}" }),
        ).filter { it.lines.isNotEmpty() }

        val summary = listOfNotNull(
            overdue.size.takeIf { it > 0 }?.let { "$it overdue" },
            (todos.size + chores.size).takeIf { it > 0 }?.let { "$it due today" },
            planned.firstOrNull()?.name,
        ).joinToString(" · ").ifEmpty { "Nothing due today" }

        return Recap(RecapKind.MORNING, "Good morning", summary, sections)
    }

    private fun night(input: RecapInput): Recap {
        val tomorrow = LocalDate.now(clock).plusDays(1)
        val items = openItems(input)
        val stillOpen = items.filter { label(it).status.let { s -> s == DueStatus.OVERDUE || s == DueStatus.TODAY } }
        val dueTomorrow = items.filter { it.dueAt?.let { d -> d.toLocalDate() == tomorrow } == true }

        // Completed today, grouped by commitment in commitment order (F6.2).
        val order = input.commitments.withIndex().associate { (i, c) -> c.id to i }
        val done = input.completedToday
            .sortedWith(compareBy<CompletionRow> { order[it.commitmentId] ?: Int.MAX_VALUE }.thenBy { it.doneAt })
            .map { "${it.commitmentName} · ${it.title}" }

        val workouts = input.sessionsToday.map { s ->
            val minutes = ((s.session.endedAt ?: s.session.startedAt) - s.session.startedAt) / 60_000
            "${s.routineName} · ${maxOf(1, minutes)} min · ${WorkoutFormat.volume(s.volume)}"
        }

        val sections = listOf(
            RecapSection("Done today", done),
            RecapSection("Workout", workouts),
            RecapSection("Still open", stillOpen.map { line(it, input) }),
            RecapSection("Due tomorrow", dueTomorrow.map { line(it, input) }),
        ).filter { it.lines.isNotEmpty() }

        val perCommitment = input.completedToday.groupingBy { it.commitmentName }.eachCount()
            .entries.sortedByDescending { it.value }
            .joinToString(", ") { "${it.value} ${it.key}" }
        val summary = listOfNotNull(
            done.size.takeIf { it > 0 }?.let { "$it done ($perCommitment)" },
            input.sessionsToday.firstOrNull()?.routineName,
            stillOpen.size.takeIf { it > 0 }?.let { "$it still open" },
            dueTomorrow.size.takeIf { it > 0 }?.let { "$it due tomorrow" },
        ).joinToString(" · ").ifEmpty { if (workouts.isNotEmpty()) workouts.first() else "Nothing to report" }

        return Recap(RecapKind.NIGHT, "Your day", summary, sections)
    }

    private fun openItems(input: RecapInput): List<TodoItem> =
        TodoItem.open(input.openTodos, clock)

    private fun label(item: TodoItem) = DueFormatter.format(item.dueAt, item.hasTime, clock, locale)

    /** "Essay draft · Study · Overdue · 2d". */
    private fun line(item: TodoItem, input: RecapInput): String {
        val commitment = input.commitments.firstOrNull { it.id == item.todo.commitmentId }?.name
        val due = label(item).text
        return listOfNotNull(item.todo.title, commitment, due.ifEmpty { null }).joinToString(" · ")
    }

    private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(clock.zone).toLocalDate()
}
