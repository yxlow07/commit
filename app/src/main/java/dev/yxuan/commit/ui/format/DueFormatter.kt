package dev.yxuan.commit.ui.format

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class DueStatus { NONE, OVERDUE, TODAY, UPCOMING }

data class DueLabel(val text: String, val status: DueStatus)

/**
 * Builds the due label shown in the app and on the widget (F3.2, F2.4).
 * The label text always states "Overdue" / "Today", so colour is never the only signal.
 */
object DueFormatter {
    private val NO_DUE = DueLabel("", DueStatus.NONE)

    fun format(dueAt: Long?, hasTime: Boolean, clock: Clock, locale: Locale = Locale.getDefault()): DueLabel {
        if (dueAt == null) return NO_DUE
        val zone = clock.zone
        val due = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDateTime()
        val now = clock.instant().atZone(zone).toLocalDateTime()
        val today = now.toLocalDate()
        val dueDate = due.toLocalDate()
        val time = if (hasTime) due.format(DateTimeFormatter.ofPattern("HH:mm", locale)) else null

        val overdue = if (hasTime) due.isBefore(now) else dueDate.isBefore(today)
        if (overdue) {
            val daysLate = ChronoUnit.DAYS.between(dueDate, today)
            val detail = if (daysLate == 0L) time else "${daysLate}d"
            return DueLabel("Overdue · $detail", DueStatus.OVERDUE)
        }

        val days = ChronoUnit.DAYS.between(today, dueDate)
        val day = when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days < 7 -> dueDate.format(DateTimeFormatter.ofPattern("EEE", locale))
            dueDate.year == today.year -> dueDate.format(DateTimeFormatter.ofPattern("d MMM", locale))
            else -> dueDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        }
        val text = if (time != null) "$day $time" else day
        return DueLabel(text, if (days == 0L) DueStatus.TODAY else DueStatus.UPCOMING)
    }

    /** Short date for chips in editors, e.g. "Mon 24 Nov". */
    fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
}
