package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {
    private val zone = ZoneId.of("Europe/London")

    private fun clockAt(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Clock =
        Clock.fixed(LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant(), zone)

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun chore(interval: Int?, lastDoneAt: Long? = null, dueAt: Long? = null, doneAt: Long? = null) =
        TodoEntity(
            id = "t", updatedAt = 0, commitmentId = "c", title = "Laundry",
            recurIntervalDays = interval, lastDoneAt = lastDoneAt, dueAt = dueAt, doneAt = doneAt,
        )

    @Test
    fun choreDoneLateInTheDayResetsAtStartOfDueDay() {
        // Ticked Monday 23 Nov 20:30, every 4 days -> due again Friday 27 Nov from midnight.
        val todo = chore(4, lastDoneAt = millis(2026, 11, 23, 20, 30))
        assertEquals(LocalDate.of(2026, 11, 27), Recurrence.nextDueDate(todo, zone, LocalDate.of(2026, 11, 23)))
        assertTrue(Recurrence.isDone(todo, clockAt(2026, 11, 26, 23, 59)))
        assertFalse(Recurrence.isDone(todo, clockAt(2026, 11, 27, 0, 1)))
    }

    @Test
    fun neverDoneChoreIsOpenAndDueOnItsFirstDateOrToday() {
        val clock = clockAt(2026, 11, 21)
        assertFalse(Recurrence.isDone(chore(3), clock))
        val (dueAt, hasTime) = Recurrence.effectiveDue(chore(3), clock)
        assertEquals(millis(2026, 11, 21), dueAt)
        assertFalse(hasTime)

        val first = millis(2026, 11, 25, 9, 0)
        assertEquals(first to false, Recurrence.effectiveDue(chore(3, dueAt = first).copy(hasTime = false), clock))
    }

    @Test
    fun overdueChoreKeepsItsMissedDueDate() {
        // Due 24 Nov, not done by 26 Nov: still open and shown as overdue from 24 Nov.
        val todo = chore(1, lastDoneAt = millis(2026, 11, 23, 8, 0))
        val clock = clockAt(2026, 11, 26)
        assertFalse(Recurrence.isDone(todo, clock))
        assertEquals(millis(2026, 11, 24), Recurrence.effectiveDue(todo, clock).first)
    }

    @Test
    fun intervalCrossesMonthEndAndDst() {
        // Clocks go back on 25 Oct 2026 in London; the due date is still exactly 7 calendar days later.
        val todo = chore(7, lastDoneAt = millis(2026, 10, 22, 23, 30))
        assertEquals(LocalDate.of(2026, 10, 29), Recurrence.nextDueDate(todo, zone, LocalDate.of(2026, 10, 22)))
        val month = chore(10, lastDoneAt = millis(2026, 11, 25, 10, 0))
        assertEquals(LocalDate.of(2026, 12, 5), Recurrence.nextDueDate(month, zone, LocalDate.of(2026, 11, 25)))
    }

    @Test
    fun oneOffTodosUseDoneAt() {
        val clock = clockAt(2026, 11, 21)
        assertFalse(Recurrence.isDone(chore(null), clock))
        assertTrue(Recurrence.isDone(chore(null, doneAt = millis(2026, 11, 20)), clock))
        assertFalse(Recurrence.isRecurring(chore(0)))
    }

    @Test
    fun openItemsHideDoneChoresAndSortByEffectiveDue() {
        val clock = clockAt(2026, 11, 21)
        val doneChore = chore(4, lastDoneAt = millis(2026, 11, 20)).copy(id = "done", title = "A")
        val dueChore = chore(2, lastDoneAt = millis(2026, 11, 19)).copy(id = "due", title = "B") // due today
        val undated = chore(null).copy(id = "undated", title = "C")
        val tomorrow = chore(null, dueAt = millis(2026, 11, 22)).copy(id = "tomorrow", title = "D")
        val open = TodoItem.open(listOf(undated, tomorrow, doneChore, dueChore), clock).map { it.todo.id }
        assertEquals(listOf("due", "tomorrow", "undated"), open)
    }

    @Test
    fun calendarRecurrenceNextMatchingDay() {
        // Mon (1) + Wed (4) + Fri (16) = 21
        val mwf = 21
        val mon = LocalDate.of(2026, 11, 23) // Monday
        assertEquals(mon, Recurrence.nextMatchingDay(mwf, mon))
        assertEquals(LocalDate.of(2026, 11, 25), Recurrence.nextMatchingDay(mwf, mon.plusDays(1))) // Tue -> Wed
        assertEquals(LocalDate.of(2026, 11, 27), Recurrence.nextMatchingDay(mwf, mon.plusDays(3))) // Thu -> Fri
        assertEquals(LocalDate.of(2026, 11, 30), Recurrence.nextMatchingDay(mwf, mon.plusDays(5))) // Sat -> next Mon
    }

    @Test
    fun calendarRecurrenceDoneResetsOnNextRecurrenceDay() {
        // Mon/Wed/Fri (21). Ticked on Monday 23 Nov at 21:00 -> due Wednesday 25 Nov from midnight.
        val todo = TodoEntity(
            id = "t", updatedAt = 0, commitmentId = "c", title = "Trash",
            recurDays = 21, lastDoneAt = millis(2026, 11, 23, 21, 0),
        )
        assertEquals(LocalDate.of(2026, 11, 25), Recurrence.nextDueDate(todo, zone, LocalDate.of(2026, 11, 23)))
        assertTrue(Recurrence.isDone(todo, clockAt(2026, 11, 24, 18, 0))) // Tuesday: done
        assertFalse(Recurrence.isDone(todo, clockAt(2026, 11, 25, 0, 1))) // Wednesday: due again
    }

    @Test
    fun calendarRecurrenceCarriesForwardWhenMissed() {
        // Due Wednesday 25 Nov. User didn't do it on Wednesday. On Thursday 26 Nov, it is still open / overdue.
        val todo = TodoEntity(
            id = "t", updatedAt = 0, commitmentId = "c", title = "Trash",
            recurDays = 21, lastDoneAt = millis(2026, 11, 23, 21, 0),
        )
        val clock = clockAt(2026, 11, 26, 10, 0) // Thursday
        assertFalse(Recurrence.isDone(todo, clock))
        assertEquals(millis(2026, 11, 25), Recurrence.effectiveDue(todo, clock).first)
    }

    @Test
    fun neverDoneCalendarChoreIsDueOnFirstMatchingDayAndOverdueWhenMissed() {
        val mwf = 21
        val zoneToday = LocalDate.of(2026, 11, 24)
        // Due Tue 24 Nov 18:00 (not a matching day) -> due Wed 25 Nov, no time.
        val tue = TodoEntity(id = "t", updatedAt = 0, commitmentId = "c", title = "Trash", recurDays = mwf,
            dueAt = millis(2026, 11, 24, 18, 0), hasTime = true)
        assertEquals(millis(2026, 11, 25) to false, Recurrence.effectiveDue(tue, clockAt(2026, 11, 24, 9)))
        // Due Mon 23 Nov 18:00 (a matching day) -> keeps the time.
        val mon = tue.copy(dueAt = millis(2026, 11, 23, 18, 0))
        assertEquals(millis(2026, 11, 23, 18, 0) to true, Recurrence.effectiveDue(mon, clockAt(2026, 11, 23, 9)))
        // Still not done on Thursday: stays due Monday (overdue), not moved to today.
        assertEquals(LocalDate.of(2026, 11, 23), Recurrence.nextDueDate(mon, zone, LocalDate.of(2026, 11, 26)))
        assertFalse(Recurrence.isDone(mon, clockAt(2026, 11, 26, 10)))
        // No due date -> today's or the next matching day.
        val undated = TodoEntity(id = "u", updatedAt = 0, commitmentId = "c", title = "Trash", recurDays = mwf)
        assertEquals(LocalDate.of(2026, 11, 25), Recurrence.nextDueDate(undated, zone, zoneToday))
    }

    @Test
    fun recurrenceLabelSupportsBothIntervalAndCalendar() {
        val intervalChore = TodoEntity(id = "1", updatedAt = 0, commitmentId = "c", title = "A", recurIntervalDays = 3)
        val singleDayChore = TodoEntity(id = "2", updatedAt = 0, commitmentId = "c", title = "B", recurIntervalDays = 1)
        val calendarChore = TodoEntity(id = "3", updatedAt = 0, commitmentId = "c", title = "C", recurDays = 21) // Mon, Wed, Fri
        assertEquals("Every 3 days", Recurrence.label(intervalChore))
        assertEquals("Every day", Recurrence.label(singleDayChore))
        assertEquals("Mon, Wed, Fri", Recurrence.label(calendarChore))
    }
}
