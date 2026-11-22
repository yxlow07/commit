package dev.yxuan.commit.ui.format

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The left-column label of a todo row: short text, and whether it is late (drawn in `danger`). */
data class ShortDue(val text: String, val late: Boolean = false) {
    companion object {
        val NONE = ShortDue("")
    }
}

/**
 * The design spec's due label rules ("Due label rules"), one rule set everywhere a todo appears:
 * Late / 18:00 / Today / Mon / 28 Nov / empty, and the weekday a todo was finished in Done and history.
 */
object DueLabels {
    private fun fmt(pattern: String, locale: Locale) = DateTimeFormatter.ofPattern(pattern, locale)

    /** Is an open todo past due? Timed: after the due time; date-only: after the due day. */
    fun isLate(dueAt: Long?, hasTime: Boolean, clock: Clock): Boolean {
        if (dueAt == null) return false
        return if (hasTime) dueAt < clock.millis()
        else Instant.ofEpochMilli(dueAt).atZone(clock.zone).toLocalDate().isBefore(LocalDate.now(clock))
    }

    /** Label for an open todo (recurring chores: their next due date). */
    fun open(dueAt: Long?, hasTime: Boolean, clock: Clock, locale: Locale = Locale.getDefault()): ShortDue {
        if (dueAt == null) return ShortDue.NONE
        if (isLate(dueAt, hasTime, clock)) return ShortDue("Late", late = true)
        val due = Instant.ofEpochMilli(dueAt).atZone(clock.zone)
        val days = ChronoUnit.DAYS.between(LocalDate.now(clock), due.toLocalDate())
        val text = when {
            days == 0L && hasTime -> due.format(fmt("HH:mm", locale))
            days == 0L -> "Today"
            days in 1..6 -> due.format(fmt("EEE", locale))
            else -> due.format(fmt("d MMM", locale))
        }
        return ShortDue(text)
    }

    /** Label for a finished todo: the weekday it was finished (older than a week: "28 Nov"). */
    fun done(doneAt: Long, clock: Clock, locale: Locale = Locale.getDefault()): ShortDue {
        val day = Instant.ofEpochMilli(doneAt).atZone(clock.zone)
        val ago = ChronoUnit.DAYS.between(day.toLocalDate(), LocalDate.now(clock))
        return ShortDue(if (ago in 0..6) day.format(fmt("EEE", locale)) else day.format(fmt("d MMM", locale)))
    }

    /** Full date for second lines and editors: "Fri 28 Nov", "Fri 28 Nov, 17:00", "28 Nov 2027". */
    fun full(dueAt: Long, hasTime: Boolean, clock: Clock, locale: Locale = Locale.getDefault()): String {
        val due = Instant.ofEpochMilli(dueAt).atZone(clock.zone)
        val date = if (due.year == LocalDate.now(clock).year) due.format(fmt("EEE d MMM", locale))
        else due.format(fmt("EEE d MMM yyyy", locale))
        return if (hasTime) "$date, ${due.format(fmt("HH:mm", locale))}" else date
    }

    /** Date-only label for buttons, e.g. "Fri 28 Nov". */
    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String = date.format(fmt("EEE d MMM", locale))

    /** "On time", "1 day late", "3 days late", "Late" (same day, after the time) or "No due date". */
    fun timeliness(dueAt: Long?, hasTime: Boolean, doneAt: Long, clock: Clock): String {
        if (dueAt == null) return "No due date"
        val zone = clock.zone
        val dueDay = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
        val doneDay = Instant.ofEpochMilli(doneAt).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(dueDay, doneDay)
        return when {
            days > 0 -> lateDays(days.toInt())
            hasTime && doneAt > dueAt -> "Late"
            else -> "On time"
        }
    }

    fun lateDays(days: Int): String = if (days == 1) "1 day late" else "$days days late"
}
