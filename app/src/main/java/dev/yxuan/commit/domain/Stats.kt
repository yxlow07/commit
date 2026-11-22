package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.ExerciseSessionPoint
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Per-commitment numbers for the history screen (F8.3). */
data class CommitmentStats(
    val commitmentId: String,
    val name: String,
    val color: Int,
    val doneThisWeek: Int,
    val onTime: Int,
    val late: Int,
    /** Current streak per recurring chore, longest first; only chores with a streak > 0. */
    val choreStreaks: List<Pair<String, Int>>,
)

/** A point on the progress chart, with personal-best flag (F8.6). */
data class ProgressPoint(val point: ExerciseSessionPoint, val isPersonalBest: Boolean)

/**
 * History calculations (F8.1–F8.3, F8.6). Pure functions over completion rows and set points.
 *
 * - On time vs late: a one-off todo is late if done after its due time (date-only: after its
 *   due day). A chore's due date at each completion is the previous completion's day + interval
 *   (or its first due date); done on a later day counts as late.
 * - Chore streak: the number of most recent completions in a row that were on time, reset to 0
 *   once the chore is overdue today.
 */
object Stats {

    fun commitmentStats(rows: List<CompletionRow>, clock: Clock): List<CommitmentStats> {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lateness = lateness(rows, zone)

        return rows.groupBy { it.commitmentId }.map { (id, list) ->
            val first = list.first()
            val streaks = list.filter { it.isRecurring }
                .groupBy { it.todoId }
                .map { (_, done) -> done.first().title to choreStreak(done, lateness, today, zone) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
            CommitmentStats(
                commitmentId = id,
                name = first.commitmentName,
                color = first.commitmentColor,
                doneThisWeek = list.count { !it.doneAt.date(zone).isBefore(weekStart) },
                onTime = list.count { lateness[it.completionId] == false },
                late = list.count { lateness[it.completionId] == true },
                choreStreaks = streaks,
            )
        }.sortedByDescending { it.doneThisWeek }
    }

    /** completionId → late? (absent when there is no due date to compare with). */
    fun lateness(rows: List<CompletionRow>, zone: ZoneId): Map<String, Boolean> =
        daysLate(rows, zone).mapValues { it.value != null }

    /**
     * completionId → days late, for the late ones: 0 when finished on the due day but after its time,
     * otherwise whole days past the due day. On-time completions map to null; completions with no due
     * date are absent.
     */
    fun daysLate(rows: List<CompletionRow>, zone: ZoneId): Map<String, Int?> {
        val result = HashMap<String, Int?>()
        rows.groupBy { it.todoId }.forEach { (_, done) ->
            val sorted = done.sortedBy { it.doneAt }
            val first = sorted.first()
            if (first.isRecurring) {
                var previous: LocalDate? = null
                for (c in sorted) {
                    val day = c.doneAt.date(zone)
                    val dueDay = previous?.let { nextDueAfter(first, it) } ?: firstDueDay(first, zone)
                    if (dueDay != null) result[c.completionId] = if (day.isAfter(dueDay)) ChronoUnit.DAYS.between(dueDay, day).toInt() else null
                    previous = day
                }
            } else {
                for (c in sorted) {
                    val dueAt = c.dueAt ?: continue
                    val days = ChronoUnit.DAYS.between(dueAt.date(zone), c.doneAt.date(zone)).toInt()
                    result[c.completionId] = when {
                        days > 0 -> days
                        c.hasTime && c.doneAt > dueAt -> 0
                        else -> null
                    }
                }
            }
        }
        return result
    }

    /** todoId → current chore streak (completions in a row on time), for chores with a streak above 0. */
    fun currentStreaks(rows: List<CompletionRow>, clock: Clock): Map<String, Int> {
        val lateness = lateness(rows, clock.zone)
        val today = LocalDate.now(clock)
        return rows.filter { it.isRecurring }.groupBy { it.todoId }
            .mapValues { (_, done) -> choreStreak(done, lateness, today, clock.zone) }
            .filterValues { it > 0 }
    }

    private fun choreStreak(done: List<CompletionRow>, lateness: Map<String, Boolean>, today: LocalDate, zone: ZoneId): Int {
        val sorted = done.sortedByDescending { it.doneAt }
        if (!sorted.first().isRecurring) return 0
        val nextDue = nextDueAfter(sorted.first(), sorted.first().doneAt.date(zone))
        if (today.isAfter(nextDue)) return 0 // overdue now: streak broken
        return sorted.takeWhile { lateness[it.completionId] != true }.size
    }

    /** A chore's due day after it was done on [doneDay]: + interval, or the next matching weekday (F4.2, F4.3). */
    private fun nextDueAfter(row: CompletionRow, doneDay: LocalDate): LocalDate {
        val mask = row.recurDays ?: 0
        return if (mask > 0) Recurrence.nextMatchingDay(mask, doneDay.plusDays(1))
        else doneDay.plusDays((row.recurIntervalDays ?: 0).toLong())
    }

    /** Due day of a chore's first completion: its due date (the first matching weekday on or after it). */
    private fun firstDueDay(row: CompletionRow, zone: ZoneId): LocalDate? {
        val due = row.dueAt?.date(zone) ?: return null
        val mask = row.recurDays ?: 0
        return if (mask > 0) Recurrence.nextMatchingDay(mask, due) else due
    }

    /** Completions per local day, for the heatmap (F8.2). */
    fun perDay(rows: List<CompletionRow>, zone: ZoneId): Map<LocalDate, Int> =
        rows.groupingBy { it.doneAt.date(zone) }.eachCount()

    /** Flags sessions where the best weight beat every earlier session (the first one is a baseline). */
    fun progress(points: List<ExerciseSessionPoint>): List<ProgressPoint> {
        var best = Double.NEGATIVE_INFINITY
        return points.sortedBy { it.startedAt }.mapIndexed { i, p ->
            val pb = i > 0 && p.bestWeight > best
            if (p.bestWeight > best) best = p.bestWeight
            ProgressPoint(p, pb)
        }
    }

    private fun Long.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}
