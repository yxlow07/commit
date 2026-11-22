package dev.yxuan.commit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderTextTest {
    private val zone = ZoneId.of("Europe/London")
    private fun millis(d: Int, h: Int, min: Int, s: Int = 0) =
        LocalDateTime.of(2026, 11, d, h, min, s).atZone(zone).toInstant().toEpochMilli()

    private val due = millis(21, 14, 0)

    @Test
    fun onTime() {
        assertEquals("Due 14:00, Study", ReminderText.label(due, millis(21, 14, 0, 1), zone, "Study"))
        assertEquals("Due 14:00", ReminderText.label(due, millis(21, 13, 59, 30), zone))
    }

    @Test
    fun earlyWithLeadTime() {
        assertEquals("Due 14:00, in 15 min, Study", ReminderText.label(due, millis(21, 13, 45), zone, "Study"))
        assertEquals("Due 14:00, in 1 h", ReminderText.label(due, millis(21, 13, 0), zone))
        assertEquals("Due 14:00, in 1 h 30 min", ReminderText.label(due, millis(21, 12, 30), zone))
    }

    @Test
    fun lateAfterSnooze() {
        assertEquals("Late, was due 14:00, Study", ReminderText.label(due, millis(21, 15, 0), zone, "Study"))
        // Snoozed past midnight: the day is named.
        assertEquals("Late, was due 21 Nov 14:00", ReminderText.label(due, millis(22, 0, 30), zone))
    }
}
