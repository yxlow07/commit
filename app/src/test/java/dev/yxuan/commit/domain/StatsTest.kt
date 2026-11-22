package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.ExerciseSessionPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class StatsTest {
    private val zone = ZoneId.of("Europe/London")
    // Saturday 21 Nov 2026, 20:00. The week started Monday 16 Nov.
    private val clock = Clock.fixed(LocalDateTime.of(2026, 11, 21, 20, 0).atZone(zone).toInstant(), zone)

    private fun millis(d: Int, h: Int = 12, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    private fun row(id: String, todo: String, done: Long, commitment: String = "study", due: Long? = null, hasTime: Boolean = false, interval: Int? = null) =
        CompletionRow(id, todo, todo, commitment, commitment.replaceFirstChar(Char::uppercase), 0, done, due, hasTime, interval)

    @Test
    fun oneOffOnTimeAndLate() {
        val rows = listOf(
            row("a", "Essay", millis(18, 23), due = millis(18)), // date-only, done same day: on time
            row("b", "Quiz", millis(19, 15), due = millis(19, 14), hasTime = true), // an hour late
            row("c", "Notes", millis(20, 9)), // no due date: not counted
        )
        val late = Stats.lateness(rows, zone)
        assertEquals(mapOf("a" to false, "b" to true), late)
    }

    @Test
    fun choreLatenessUsesPreviousCompletionPlusInterval() {
        // Every 2 days: done 14th, 16th (on time), 19th (due 18th: late), 21st (on time).
        val rows = listOf(14, 16, 19, 21).mapIndexed { i, d -> row("c$i", "Laundry", millis(d), "home", interval = 2) }
        val late = Stats.lateness(rows, zone)
        assertEquals(listOf(false, false, true, false), rows.map { late[it.completionId] ?: false })

        val stats = Stats.commitmentStats(rows, clock).single()
        // Streak counts back from the newest until a late one: only the 21st.
        assertEquals(listOf("Laundry" to 1), stats.choreStreaks)
        assertEquals(3, stats.doneThisWeek) // 16th (Monday), 19th and 21st; the 14th was last week
        // The first completion has nothing to compare with (no due date, no earlier completion).
        assertEquals(2 to 1, stats.onTime to stats.late)
    }

    @Test
    fun weekdayChoreLatenessUsesNextMatchingDay() {
        // Mon + Thu (mask 9), first due Mon 9 Nov: done Tue 10th (late), Thu 12th, Mon 16th, Thu 19th (all on time).
        val rows = listOf(10, 12, 16, 19).mapIndexed { i, d ->
            row("w$i", "Bins", millis(d, 12), "home", due = millis(9)).copy(recurIntervalDays = null, recurDays = 9)
        }
        val late = Stats.lateness(rows, zone)
        assertEquals(listOf(true, false, false, false), rows.map { late[it.completionId] ?: false })
        val stats = Stats.commitmentStats(rows, clock).single()
        assertEquals(listOf("Bins" to 3), stats.choreStreaks) // next due Mon 23rd: not overdue on Sat 21st
        assertEquals(3 to 1, stats.onTime to stats.late)
    }

    @Test
    fun daysLateAndCurrentStreaks() {
        val rows = listOf(
            row("a", "Essay", millis(18, 23), due = millis(18)), // on time
            row("b", "Quiz", millis(19, 15), due = millis(19, 14), hasTime = true), // same day, after the time
            row("c", "Lab", millis(20, 9), due = millis(17)), // 3 days late
        ) + listOf(17, 19, 21).mapIndexed { i, d -> row("p$i", "Plants", millis(d), "home", interval = 2) }
        val late = Stats.daysLate(rows, zone)
        assertEquals(null, late["a"])
        assertEquals(0, late["b"])
        assertEquals(3, late["c"])
        assertEquals(mapOf("Plants" to 3), Stats.currentStreaks(rows, clock))
    }

    @Test
    fun thisWeekCountsFromMonday() {
        val rows = listOf(15, 16, 19, 21).mapIndexed { i, d -> row("r$i", "Read $i", millis(d)) }
        val stats = Stats.commitmentStats(rows, clock).single()
        assertEquals(3, stats.doneThisWeek) // 16th (Mon), 19th, 21st; the 15th was last week
    }

    @Test
    fun overdueChoreHasNoStreak() {
        // Every 1 day, last done on the 19th: due the 20th, overdue on the 21st.
        val rows = listOf(17, 18, 19).mapIndexed { i, d -> row("o$i", "Water plants", millis(d), "home", interval = 1) }
        assertEquals(emptyList<Pair<String, Int>>(), Stats.commitmentStats(rows, clock).single().choreStreaks)
    }

    @Test
    fun perDayCountsLocalDays() {
        val rows = listOf(row("x", "A", millis(20, 23, 30)), row("y", "B", millis(20, 8)), row("z", "C", millis(21, 0, 30)))
        assertEquals(mapOf(LocalDate.of(2026, 11, 20) to 2, LocalDate.of(2026, 11, 21) to 1), Stats.perDay(rows, zone))
    }

    @Test
    fun personalBestsAfterBaseline() {
        fun p(day: Int, weight: Double) = ExerciseSessionPoint("Bench", "s$day", millis(day), weight, 8, weight * 24)
        val result = Stats.progress(listOf(p(10, 60.0), p(12, 62.5), p(14, 62.5), p(17, 60.0), p(19, 65.0)))
        assertEquals(listOf(false, true, false, false, true), result.map { it.isPersonalBest })
    }
}
