package dev.yxuan.commit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalUnitTest {
    @Test
    fun splitPicksTheLargestExactUnit() {
        assertEquals(1 to IntervalUnit.DAY, IntervalUnit.split(1))
        assertEquals(5 to IntervalUnit.DAY, IntervalUnit.split(5))
        assertEquals(2 to IntervalUnit.WEEK, IntervalUnit.split(14))
        assertEquals(1 to IntervalUnit.MONTH, IntervalUnit.split(30))
        assertEquals(1 to IntervalUnit.YEAR, IntervalUnit.split(365))
        assertEquals(3 to IntervalUnit.MONTH, IntervalUnit.split(90))
        assertEquals(10 to IntervalUnit.WEEK, IntervalUnit.split(70)) // 70 is weeks, not months
        assertEquals(1 to IntervalUnit.DAY, IntervalUnit.split(0)) // never below a day
    }

    @Test
    fun describeAndLabels() {
        assertEquals("day", IntervalUnit.describe(1))
        assertEquals("week", IntervalUnit.describe(7))
        assertEquals("2 weeks", IntervalUnit.describe(14))
        assertEquals("3 days", IntervalUnit.describe(3))
        assertEquals("Every 2 weeks", Recurrence.intervalLabel(14))
        assertEquals("Every month", Recurrence.intervalLabel(30))
        assertEquals("Every 4 days", Recurrence.intervalLabel(4))
        assertEquals("Every day", Recurrence.intervalLabel(1))
    }
}
