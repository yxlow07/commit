package dev.yxuan.commit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RetentionTest {
    private val zone = ZoneId.of("UTC")
    // 2026-11-21T12:00:00Z
    private val clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), zone)

    @Test
    fun retentionCutoffCalculatesExactly365DaysAgoAtStartOfDay() {
        val cutoff = RetentionManager.cutoffMillis(clock, days = 365)
        val expected = LocalDate.of(2025, 11, 21).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected, cutoff)
    }

    @Test
    fun retentionCutoffSupportsCustomDays() {
        val cutoff30 = RetentionManager.cutoffMillis(clock, days = 30)
        val expected30 = LocalDate.of(2026, 10, 22).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expected30, cutoff30)
    }
}
