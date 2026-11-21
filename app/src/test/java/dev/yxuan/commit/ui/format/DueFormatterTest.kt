package dev.yxuan.commit.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class DueFormatterTest {
    private val zone = ZoneId.of("Europe/London")

    // "Now" is Saturday 21 Nov 2026, 12:00.
    private val clock = Clock.fixed(LocalDateTime.of(2026, 11, 21, 12, 0).atZone(zone).toInstant(), zone)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun format(dueAt: Long?, hasTime: Boolean) = DueFormatter.format(dueAt, hasTime, clock, Locale.UK)

    @Test
    fun noDueDate() {
        assertEquals(DueLabel("", DueStatus.NONE), format(null, false))
    }

    @Test
    fun todayWithAndWithoutTime() {
        assertEquals(DueLabel("Today", DueStatus.TODAY), format(at(2026, 11, 21), false))
        assertEquals(DueLabel("Today 14:00", DueStatus.TODAY), format(at(2026, 11, 21, 14), true))
    }

    @Test
    fun timedEarlierTodayIsOverdue() {
        assertEquals(DueLabel("Overdue · 09:30", DueStatus.OVERDUE), format(at(2026, 11, 21, 9, 30), true))
    }

    @Test
    fun pastDaysAreOverdueWithDayCount() {
        assertEquals(DueLabel("Overdue · 2d", DueStatus.OVERDUE), format(at(2026, 11, 19), false))
        assertEquals(DueLabel("Overdue · 1d", DueStatus.OVERDUE), format(at(2026, 11, 20, 23, 0), true))
    }

    @Test
    fun upcoming() {
        assertEquals(DueLabel("Tomorrow", DueStatus.UPCOMING), format(at(2026, 11, 22), false))
        assertEquals(DueLabel("Tomorrow 09:00", DueStatus.UPCOMING), format(at(2026, 11, 22, 9), true))
        assertEquals(DueLabel("Thu", DueStatus.UPCOMING), format(at(2026, 11, 26), false))
        assertEquals(DueLabel("5 Dec", DueStatus.UPCOMING), format(at(2026, 12, 5), false))
        assertEquals(DueLabel("10 Jan 2027 08:15", DueStatus.UPCOMING), format(at(2027, 1, 10, 8, 15), true))
    }
}
