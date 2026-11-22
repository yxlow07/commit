package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.SessionSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.data.db.entity.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class RecapTest {
    private val zone = ZoneId.of("Europe/London")
    // Saturday 21 Nov 2026.
    private val morningClock = clockAt(7, 30)
    private val nightClock = clockAt(22, 0)

    private fun clockAt(h: Int, m: Int): Clock =
        Clock.fixed(LocalDateTime.of(2026, 11, 21, h, m).atZone(zone).toInstant(), zone)

    private fun millis(d: Int, h: Int = 0, m: Int = 0) =
        LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()

    private val study = CommitmentEntity(id = "study", updatedAt = 0, name = "Study", color = 0, sortOrder = 0)
    private val home = CommitmentEntity(id = "home", updatedAt = 0, name = "Home", color = 0, sortOrder = 1)

    private fun todo(id: String, commitment: String = "study", dueAt: Long? = null, hasTime: Boolean = false, interval: Int? = null, lastDone: Long? = null) =
        TodoEntity(id = id, updatedAt = 0, commitmentId = commitment, title = id, dueAt = dueAt, hasTime = hasTime, recurIntervalDays = interval, lastDoneAt = lastDone)

    private val todos = listOf(
        todo("Essay", dueAt = millis(19)), // overdue 2 days
        todo("Quiz", dueAt = millis(21, 14), hasTime = true), // today 14:00
        todo("Read", dueAt = millis(21)), // today, date only (F7.2)
        todo("Laundry", "home", interval = 2, lastDone = millis(19, 9)), // chore due today
        todo("Dishes", "home", interval = 3, lastDone = millis(20, 9)), // done, due 23rd
        todo("Slides", dueAt = millis(22, 10), hasTime = true), // tomorrow
        todo("Someday"), // undated
    )
    private val push = RoutineEntity(
        id = "push", updatedAt = 0, commitmentId = "gym", name = "Push Day", sortOrder = 0,
        plannedDays = Weekdays.bit(DayOfWeek.SATURDAY) or Weekdays.bit(DayOfWeek.TUESDAY),
    )
    private val legs = RoutineEntity(id = "legs", updatedAt = 0, commitmentId = "gym", name = "Legs", sortOrder = 1, plannedDays = Weekdays.bit(DayOfWeek.MONDAY))

    private fun input(completed: List<CompletionRow> = emptyList(), sessions: List<SessionSummary> = emptyList()) =
        RecapInput(todos, listOf(study, home), listOf(push, legs), completed, sessions)

    @Test
    fun morningListsOverdueTodayChoresAndPlannedWorkout() {
        val recap = RecapBuilder(morningClock, Locale.UK).build(RecapKind.MORNING, input())
        val byTitle = recap.sections.associate { it.title to it.lines }
        assertEquals(listOf("Essay, Late"), byTitle["Overdue"])
        assertEquals(listOf("Read, Study", "Quiz, 14:00"), byTitle["Due today"])
        assertEquals(listOf("Laundry, Home"), byTitle["Chores"])
        assertEquals(listOf("Push Day, workout"), byTitle["Workout"])
        assertEquals("4 things today, 1 overdue", recap.title)
        assertEquals("Essay, Read, Quiz and 2 more", recap.summary)
        assertFalse(recap.isEmpty)
    }

    @Test
    fun nightSummarisesTheDay() {
        val completed = listOf(
            CompletionRow("c1", "Notes", "Notes", "study", "Study", 0, millis(21, 10), null, false, null),
            CompletionRow("c2", "Bins", "Bins", "home", "Home", 0, millis(21, 9), null, false, null),
            CompletionRow("c3", "Flashcards", "Flashcards", "study", "Study", 0, millis(21, 18), null, false, null),
        )
        val session = SessionSummary(
            WorkoutSessionEntity(id = "s", updatedAt = 0, routineId = "push", startedAt = millis(21, 18), endedAt = millis(21, 18, 42)),
            routineName = "Push Day", volume = 3450.0, setCount = 12,
        )
        val recap = RecapBuilder(nightClock, Locale.UK).build(RecapKind.NIGHT, input(completed, listOf(session)))
        val byTitle = recap.sections.associate { it.title to it.lines }
        // Grouped by commitment order (Study before Home), then by time.
        assertEquals(listOf("Notes, Study", "Flashcards, Study", "Bins, Home"), byTitle["Done today"])
        assertEquals(listOf("Push Day, 42 min, 3,450 kg"), byTitle["Workout"])
        // At 22:00 the 14:00 quiz is overdue too; the chore is still due today.
        assertEquals(4, byTitle["Still open"]!!.size)
        assertEquals(listOf("Slides, Sun"), byTitle["Due tomorrow"])
        assertEquals("3 done today, 4 still open", recap.title)
        assertEquals("Still open: Essay, Laundry, Read and 1 more. Due tomorrow: Slides. Push Day took 42 min, 3,450 kg.", recap.body)
    }

    @Test
    fun emptyDayIsSkippable() {
        val empty = RecapInput(listOf(todo("Someday")), listOf(study), emptyList(), emptyList(), emptyList())
        assertTrue(RecapBuilder(morningClock).build(RecapKind.MORNING, empty).isEmpty)
        assertTrue(RecapBuilder(nightClock).build(RecapKind.NIGHT, empty).isEmpty)
    }

    @Test
    fun weekdayMask() {
        val mask = Weekdays.bit(DayOfWeek.MONDAY) or Weekdays.bit(DayOfWeek.THURSDAY)
        assertEquals(1 or 8, mask)
        assertTrue(Weekdays.isPlanned(mask, LocalDate.of(2026, 11, 23))) // Monday
        assertFalse(Weekdays.isPlanned(mask, LocalDate.of(2026, 11, 24)))
        assertEquals("Mon, Thu", Weekdays.label(mask, Locale.UK))
        assertEquals(1, Weekdays.toggle(mask, DayOfWeek.THURSDAY))
    }
}
