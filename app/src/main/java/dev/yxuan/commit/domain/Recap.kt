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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class RecapKind { MORNING, NIGHT }

/**
 * One recap row, drawn like a todo row: [status] in the left column ("Late", "14:00", "Thu"), the
 * [title], and a [detail] line tinted toward [color] (the commitment's). [late] colours the status
 * red. [text] is the plain one-line form used by the notification ("Quiz, 14:00").
 */
data class RecapLine(
    val status: String,
    val title: String,
    val detail: String? = null,
    val late: Boolean = false,
    val color: Int? = null,
    val text: String = listOfNotNull(title, detail).joinToString(", "),
)

data class RecapSection(val title: String, val items: List<RecapLine>) {
    val lines: List<String> get() = items.map { it.text }
}

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
    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }
    val lines: List<String> get() = sections.flatMap { s -> s.lines }
}

/** Everything a recap is built from; loaded from Room at the moment the recap fires (F6.3). */
data class RecapInput(
    /** Not-completed todos of active commitments (one-off undone + all recurring). */
    val openTodos: List<TodoEntity>,
    val commitments: List<CommitmentEntity>,
    val routines: List<RoutineEntity>,
    /** Completions done today (or on the day being reviewed). */
    val completedToday: List<CompletionRow>,
    /** Workout sessions that ended today (or on the day being reviewed). */
    val sessionsToday: List<SessionSummary>,
)

/**
 * Builds the morning (F6.1) and night (F6.2) recaps, and the review of a past day. Pure: no
 * Android, no database, so the same code backs the notification, the recap screen and the tests.
 */
class RecapBuilder(private val clock: Clock, private val locale: Locale = Locale.getDefault()) {

    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

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

        val sections = listOf(
            RecapSection("Overdue", overdue.map { openLine(it, input).copy(text = "${it.todo.title}, Late") }),
            RecapSection("Due today", todos.map { openLine(it, input) }),
            RecapSection("Chores", chores.map { chore(it, input) }),
            RecapSection("Workout", planned.map { r ->
                RecapLine("Today", r.name, commitment(r.commitmentId, input)?.name ?: "Workout", color = commitment(r.commitmentId, input)?.color, text = "${r.name}, workout")
            }),
            // Viewed later in the day, the morning recap also shows what's already done.
            RecapSection("Done today", doneLines(input) + workoutLines(input)),
        ).filter { it.items.isNotEmpty() }

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
        val done = doneLines(input)
        val workouts = workoutLines(input)

        val sections = listOf(
            RecapSection("Done today", done),
            RecapSection("Workout", workouts),
            RecapSection("Still open", stillOpen.map { openLine(it, input) }),
            RecapSection("Due tomorrow", dueTomorrow.map { openLine(it, input) }),
        ).filter { it.items.isNotEmpty() }

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
            input.sessionsToday.firstOrNull()?.let { s -> "${s.routineName} took ${minutes(s)} min, ${WorkoutFormat.volume(s.volume)}." },
        ).joinToString(" ").ifEmpty { if (done.isNotEmpty()) "Everything due today is done." else "" }

        return Recap(RecapKind.NIGHT, title, body, sections, body)
    }

    /**
     * Looking back at [day] (from the History grid): what got done, the workouts, and what was due
     * that day and is still not done. Today is the night recap; the future has nothing to show.
     * [input] holds that day's completions and sessions.
     */
    fun day(day: LocalDate, input: RecapInput): Recap {
        val today = LocalDate.now(clock)
        if (!day.isBefore(today)) return night(input)
        val done = doneLines(input)
        val workouts = workoutLines(input)
        val missed = openItems(input).filter { !it.isRecurring && it.dueAt?.toLocalDate() == day }
        val sections = listOf(
            RecapSection("Done", done),
            RecapSection("Workout", workouts),
            RecapSection("Was due, still open", missed.map { openLine(it, input) }),
        ).filter { it.items.isNotEmpty() }
        val title = listOfNotNull(
            "${done.size} done".takeIf { done.isNotEmpty() },
            count(workouts.size, "workout").takeIf { workouts.isNotEmpty() },
            "${missed.size} missed".takeIf { missed.isNotEmpty() },
        ).joinToString(", ").ifEmpty { "Nothing done that day" }
        return Recap(RecapKind.NIGHT, title, title, sections)
    }

    // ---- lines ----

    /** Completions in commitment order: done time on the left ("Late" in red when finished after the due time). */
    private fun doneLines(input: RecapInput): List<RecapLine> {
        val order = input.commitments.withIndex().associate { (i, c) -> c.id to i }
        return input.completedToday
            .sortedWith(compareBy<CompletionRow> { order[it.commitmentId] ?: Int.MAX_VALUE }.thenBy { it.doneAt })
            .map { row ->
                val timeliness = if (row.isRecurring) "On time" else DueLabels.timeliness(row.dueAt, row.hasTime, row.doneAt, clock)
                val late = timeliness != "On time" && timeliness != "No due date"
                RecapLine(
                    status = if (late) "Late" else time(row.doneAt),
                    title = row.title,
                    detail = listOfNotNull(row.commitmentName, timeliness.takeIf { late && it != "Late" }).joinToString(", "),
                    late = late,
                    color = row.commitmentColor,
                    text = "${row.title}, ${row.commitmentName}",
                )
            }
    }

    /** Sessions: start time on the left, then "42 min, 3,450 kg". */
    private fun workoutLines(input: RecapInput): List<RecapLine> = input.sessionsToday.map { s ->
        val detail = "${minutes(s)} min, ${WorkoutFormat.volume(s.volume)}"
        val color = input.routines.firstOrNull { it.id == s.session.routineId }?.let { commitment(it.commitmentId, input)?.color }
        RecapLine(time(s.session.startedAt), s.routineName, detail, color = color, text = "${s.routineName}, $detail")
    }

    /** An open todo: its due label on the left (red when late), the commitment underneath. */
    private fun openLine(item: TodoItem, input: RecapInput): RecapLine {
        val due = DueLabels.open(item.dueAt, item.hasTime, clock, locale)
        val c = commitment(item.todo.commitmentId, input)
        val detail = listOfNotNull(c?.name, if (item.isRecurring) Recurrence.label(item.todo) else null).joinToString(", ").ifEmpty { null }
        return RecapLine(due.text.ifEmpty { "No date" }, item.todo.title, detail, late = due.late, color = c?.color, text = textLine(item, input))
    }

    private fun chore(item: TodoItem, input: RecapInput): RecapLine =
        openLine(item, input).copy(text = "${item.todo.title}, ${commitment(item.todo.commitmentId, input)?.name ?: "chore"}")

    private fun openItems(input: RecapInput): List<TodoItem> = TodoItem.open(input.openTodos, clock)

    private fun isLate(item: TodoItem) = DueLabels.isLate(item.dueAt, item.hasTime, clock)

    private fun commitment(id: String, input: RecapInput) = input.commitments.firstOrNull { it.id == id }

    private fun minutes(s: SessionSummary) = maxOf(1, ((s.session.endedAt ?: s.session.startedAt) - s.session.startedAt) / 60_000)

    private fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalTime().format(timeFormat)

    /** "Quiz, 14:00", or "Read, Study" when the label would only say "Today". */
    private fun textLine(item: TodoItem, input: RecapInput): String {
        val label = DueLabels.open(item.dueAt, item.hasTime, clock, locale).text
        val right = when (label) {
            "", "Today" -> commitment(item.todo.commitmentId, input)?.name
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
