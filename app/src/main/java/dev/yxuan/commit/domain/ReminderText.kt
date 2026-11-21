package dev.yxuan.commit.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Text of a due-time reminder, worked out when it fires (F7.1, F7.4): early with a lead time
 * ("Due in 15 min · 14:00"), on time ("Due now · 14:00"), or late after a snooze ("Overdue · was due 14:00").
 */
object ReminderText {
    private const val ON_TIME_MS = 60_000L
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm")

    fun label(dueAt: Long, now: Long, zone: ZoneId): String {
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val sameDay = due.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = due.format(if (sameDay) TIME else DAY_TIME)
        val diff = dueAt - now
        return when {
            abs(diff) <= ON_TIME_MS -> "Due now · $time"
            diff > 0 -> "Due in ${duration(diff)} · $time"
            else -> "Overdue · was due $time"
        }
    }

    /** Rounded to the nearest minute: "15 min", "1 h", "1 h 30 min". */
    private fun duration(ms: Long): String {
        val minutes = (ms + 30_000) / 60_000
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }
}
