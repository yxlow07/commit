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
        assertEquals("Due now · 14:00", ReminderText.label(due, millis(21, 14, 0, 1), zone))
        assertEquals("Due now · 14:00", ReminderText.label(due, millis(21, 13, 59, 30), zone))
    }

    @Test
    fun earlyWithLeadTime() {
        assertEquals("Due in 15 min · 14:00", ReminderText.label(due, millis(21, 13, 45), zone))
        assertEquals("Due in 1 h · 14:00", ReminderText.label(due, millis(21, 13, 0), zone))
        assertEquals("Due in 1 h 30 min · 14:00", ReminderText.label(due, millis(21, 12, 30), zone))
    }

    @Test
    fun lateAfterSnooze() {
        assertEquals("Overdue · was due 14:00", ReminderText.label(due, millis(21, 15, 0), zone))
        // Snoozed past midnight: the day is named.
        assertEquals("Overdue · was due 21 Nov 14:00", ReminderText.label(due, millis(22, 0, 30), zone))
    }
}
