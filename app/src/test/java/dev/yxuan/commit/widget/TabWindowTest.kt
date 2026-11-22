package dev.yxuan.commit.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class TabWindowTest {
    // Five tabs, 60 dp each (selected ones would be wider; equal widths keep the cases readable).
    private val five = List(5) { 60f }
    private fun shown(slots: List<TabSlot>) = slots.map { if (it.partial) "${it.index}~" else "${it.index}" }

    @Test
    fun everythingFits() {
        assertEquals(listOf("0", "1", "2"), shown(tabWindow(List(3) { 60f }, 1, available = 300f, gap = 14f)))
    }

    @Test
    fun firstTabSelectedShowsWhatFitsAndCutsTheNext() {
        // 60 + 14 + 60 + 14 + 60 = 208; 260 - 208 - 14 = 38 left: tab 3 shown cut off.
        assertEquals(listOf("0", "1", "2", "3~"), shown(tabWindow(five, 0, available = 260f, gap = 14f)))
    }

    @Test
    fun windowStartsOneBeforeTheSelectedTab() {
        assertEquals(listOf("1", "2", "3~"), shown(tabWindow(five, 2, available = 220f, gap = 14f)))
    }

    @Test
    fun atTheEndTheRowSlidesLeftToStayFull() {
        // Selecting the last tab shows the last three, not just the last two.
        assertEquals(listOf("2", "3", "4"), shown(tabWindow(five, 4, available = 220f, gap = 14f)))
    }

    @Test
    fun selectedTabAlwaysFitsWhenThePreviousOneIsTooWide() {
        val widths = listOf(60f, 200f, 120f)
        assertEquals("2", shown(tabWindow(widths, 2, available = 150f, gap = 14f)).first())
    }

    @Test
    fun noRoomForTheNextTabCutsTheLastOneInstead() {
        // 60 + 14 + 60 = 134; 150 - 134 - 14 = 2 left: tab 1 is cut so the row still hints at more.
        assertEquals(listOf("0", "1~"), shown(tabWindow(five, 0, available = 150f, gap = 14f)))
    }

    @Test
    fun theSelectedTabIsNeverTheCutOne() {
        // Only the selected tab fits: nothing is cut.
        assertEquals(listOf("0"), shown(tabWindow(List(3) { 100f }, 0, available = 120f, gap = 14f)))
    }
}
