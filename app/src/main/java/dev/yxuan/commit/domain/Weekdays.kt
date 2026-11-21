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

    /** "Mon · Thu", or "" when no day is set. */
    fun label(mask: Int, locale: Locale = Locale.getDefault()): String =
        DayOfWeek.entries.filter { contains(mask, it) }
            .joinToString(" · ") { it.getDisplayName(TextStyle.SHORT, locale) }
}
