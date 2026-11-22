package dev.yxuan.commit.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Weekday bitmask used for a routine's planned days: Monday = 1, Tuesday = 2 … Sunday = 64. */
object Weekdays {
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun isPlanned(mask: Int, date: LocalDate): Boolean = contains(mask, date.dayOfWeek)

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    const val EVERY_DAY = 127 // Mon … Sun
    const val WEEKDAYS = 31 // Mon … Fri
    const val WEEKENDS = 96 // Sat, Sun

    /**
     * "Every day", "Weekdays", "Weekends" for those sets; otherwise "Mon, Thu" (spec: metadata
     * joined with commas), or "" when no day is set.
     */
    fun label(mask: Int, locale: Locale = Locale.getDefault()): String = when (mask) {
        EVERY_DAY -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        else -> DayOfWeek.entries.filter { contains(mask, it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}
