package dev.yxuan.commit.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleTabsTest {
    private val tabs = listOf("A", "B", "C", "D", "E")

    @Test
    fun allTabsFit() {
        assertEquals(tabs, visibleTabs(tabs, "E", 5))
    }

    @Test
    fun selectedWithinFirstTabs() {
        assertEquals(listOf("A", "B", "C"), visibleTabs(tabs, "B", 3))
    }

    @Test
    fun selectedBeyondLimitReplacesLastVisible() {
        assertEquals(listOf("A", "B", "E"), visibleTabs(tabs, "E", 3))
    }
}
