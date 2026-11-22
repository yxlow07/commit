package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.SessionSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.ui.format.DueLabels
import dev.yxuan.commit.ui.format.WorkoutFormat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

enum class RecapKind { MORNING, NIGHT }

data class RecapSection(val title: String, val lines: List<String>)

/**
 * A daily recap (F6). [title] is the notification title ("4 things today, 1 overdue"); [summary] the
 * collapsed text; [body] the plain sentences of the night recap.
 */
data class Recap(
    val kind: RecapKind,
    val title: String,
    val summary: String,
    val sections: List<RecapSection>,
    val body: String = summary,
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
        val overdue = items.filter { isLate(it) }
        val dueToday = items.filter { !isLate(it) && it.dueAt?.toLocalDate() == today }
        val todos = dueToday.filterNot { it.isRecurring }
        val chores = dueToday.filter { it.isRecurring }
        val planned = input.routines.filter { Weekdays.isPlanned(it.plannedDays, today) }

        // One line per item: title, then the due label (a time, "Late") or the commitment.
        val sections = listOf(
            RecapSection("Overdue", overdue.map { "${it.todo.title}, Late" }),
            RecapSection("Due today", todos.map { line(it, input) }),
            RecapSection("Chores", chores.map { "${it.todo.title}, ${commitmentName(it, input) ?: "chore"}" }),
            RecapSection("Workout", planned.map { "${it.name}, workout" }),
        ).filter { it.lines.isNotEmpty() }

        val things = todos.size + chores.size + planned.size
        val title = when {
            things == 0 && overdue.isEmpty() -> "Nothing due today"
            things == 0 -> "${overdue.size} overdue"
            else -> "${count(things, "thing")} today" + if (overdue.isNotEmpty()) ", ${overdue.size} overdue" else ""
        }
        val names = (overdue + todos + chores).map { it.todo.title } + planned.map { it.name }
        return Recap(RecapKind.MORNING, title, listSentence(names), sections)
    }

    private fun night(input: RecapInput): Recap {
        val today = LocalDate.now(clock)
        val tomorrow = today.plusDays(1)
        val items = openItems(input)
        val stillOpen = items.filter { isLate(it) || it.dueAt?.toLocalDate() == today }
        val dueTomorrow = items.filter { !isLate(it) && it.dueAt?.toLocalDate() == tomorrow }

        // Completed today, grouped by commitment in commitment order (F6.2).
        val order = input.commitments.withIndex().associate { (i, c) -> c.id to i }
        val done = input.completedToday
            .sortedWith(compareBy<CompletionRow> { order[it.commitmentId] ?: Int.MAX_VALUE }.thenBy { it.doneAt })
            .map { "${it.title}, ${it.commitmentName}" }

        val workouts = input.sessionsToday.map { s ->
            val minutes = maxOf(1, ((s.session.endedAt ?: s.session.startedAt) - s.session.startedAt) / 60_000)
            Triple(s.routineName, minutes, WorkoutFormat.volume(s.volume))
        }

        val sections = listOf(
            RecapSection("Done today", done),
            RecapSection("Workout", workouts.map { (name, min, vol) -> "$name, $min min, $vol" }),
            RecapSection("Still open", stillOpen.map { line(it, input) }),
            RecapSection("Due tomorrow", dueTomorrow.map { line(it, input) }),
        ).filter { it.lines.isNotEmpty() }

        val title = when {
            done.isEmpty() && stillOpen.isEmpty() -> if (workouts.isNotEmpty()) "Workout done today" else "Nothing to report"
            done.isEmpty() -> "Nothing done today, ${stillOpen.size} still open"
            stillOpen.isEmpty() -> "${done.size} done today"
            else -> "${done.size} done today, ${stillOpen.size} still open"
        }
        // Two or three plain sentences: still open, due tomorrow, the workout.
        val body = listOfNotNull(
            stillOpen.takeIf { it.isNotEmpty() }?.let { "Still open: ${listSentence(it.map { i -> i.todo.title })}." },
            dueTomorrow.takeIf { it.isNotEmpty() }?.let { "Due tomorrow: ${listSentence(it.map { i -> i.todo.title })}." },
            workouts.firstOrNull()?.let { (name, min, vol) -> "$name took $min min, $vol." },
        ).joinToString(" ").ifEmpty { if (done.isNotEmpty()) "Everything due today is done." else "" }

        return Recap(RecapKind.NIGHT, title, body, sections, body)
    }

    private fun openItems(input: RecapInput): List<TodoItem> =
        TodoItem.open(input.openTodos, clock)

    private fun isLate(item: TodoItem) = DueLabels.isLate(item.dueAt, item.hasTime, clock)

    private fun commitmentName(item: TodoItem, input: RecapInput) =
        input.commitments.firstOrNull { it.id == item.todo.commitmentId }?.name

    /** "Quiz, 14:00", or "Read, Study" when the label would only say "Today". */
    private fun line(item: TodoItem, input: RecapInput): String {
        val label = DueLabels.open(item.dueAt, item.hasTime, clock, locale).text
        val right = when (label) {
            "", "Today" -> commitmentName(item, input)
            else -> label
        }
        return listOfNotNull(item.todo.title, right).joinToString(", ")
    }

    /** "Essay", "Essay and Quiz", "Essay, Quiz and Read", "Essay, Quiz, Read and 2 more". */
    private fun listSentence(names: List<String>): String = when {
        names.isEmpty() -> ""
        names.size == 1 -> names[0]
        names.size <= 3 -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        else -> names.take(3).joinToString(", ") + " and ${names.size - 3} more"
    }

    private fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(clock.zone).toLocalDate()
}
