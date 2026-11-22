package dev.yxuan.commit.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class DueLabelsTest {
    private val zone = ZoneId.of("Europe/London")
    // Saturday 21 Nov 2026, 12:00
    private val clock = Clock.fixed(LocalDateTime.of(2026, 11, 21, 12, 0).atZone(zone).toInstant(), zone)
    private val uk = Locale.UK
    private fun at(d: Int, h: Int = 0, m: Int = 0, month: Int = 11, y: Int = 2026) =
        LocalDateTime.of(y, month, d, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun openRulesTable() {
        assertEquals(ShortDue("Late", true), DueLabels.open(at(21, 11, 0), true, clock, uk)) // earlier today
        assertEquals(ShortDue("Late", true), DueLabels.open(at(20), false, clock, uk)) // yesterday, no time
        assertEquals(ShortDue("18:00"), DueLabels.open(at(21, 18, 0), true, clock, uk))
        assertEquals(ShortDue("Today"), DueLabels.open(at(21), false, clock, uk))
        assertEquals(ShortDue("Sun"), DueLabels.open(at(22, 9, 0), true, clock, uk)) // tomorrow: weekday
        assertEquals(ShortDue("Fri"), DueLabels.open(at(27), false, clock, uk)) // 6 days
        assertEquals(ShortDue("28 Nov"), DueLabels.open(at(28), false, clock, uk)) // 7 days
        assertEquals(ShortDue.NONE, DueLabels.open(null, false, clock, uk))
    }

    @Test
    fun doneShowsTheWeekdayFinished() {
        assertEquals("Thu", DueLabels.done(at(19, 21, 30), clock, uk).text)
        assertEquals("Sat", DueLabels.done(at(21, 9, 0), clock, uk).text)
        assertEquals("10 Nov", DueLabels.done(at(10, 9, 0), clock, uk).text)
    }

    @Test
    fun fullDate() {
        assertEquals("Sat 28 Nov, 17:00", DueLabels.full(at(28, 17, 0), true, clock, uk))
        assertEquals("Sat 28 Nov", DueLabels.full(at(28), false, clock, uk))
        assertEquals("Sun 3 Jan 2027", DueLabels.full(at(3, month = 1, y = 2027), false, clock, uk))
    }

    @Test
    fun timeliness() {
        assertEquals("On time", DueLabels.timeliness(at(20), false, at(20, 23, 0), clock))
        assertEquals("1 day late", DueLabels.timeliness(at(19), false, at(20, 9, 0), clock))
        assertEquals("3 days late", DueLabels.timeliness(at(17, 23, 59), true, at(20, 9, 0), clock))
        assertEquals("Late", DueLabels.timeliness(at(20, 14, 0), true, at(20, 15, 0), clock))
        assertEquals("No due date", DueLabels.timeliness(null, false, at(20), clock))
    }
}
