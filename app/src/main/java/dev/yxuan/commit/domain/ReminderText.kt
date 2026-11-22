package dev.yxuan.commit.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Body of a due-time reminder, worked out when it fires (F7.1, F7.4; design spec "Notifications"):
 * "Due 18:00, Study" on time, "Due 18:00, in 15 min, Study" with a lead time, and
 * "Late, was due 18:00, Study" after a snooze.
 */
object ReminderText {
    private const val ON_TIME_MS = 60_000L
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val DAY_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm")

    fun label(dueAt: Long, now: Long, zone: ZoneId, commitment: String? = null): String {
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val sameDay = due.toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = due.format(if (sameDay) TIME else DAY_TIME)
        val diff = dueAt - now
        val parts = when {
            abs(diff) <= ON_TIME_MS -> listOf("Due $time")
            diff > 0 -> listOf("Due $time", "in ${duration(diff)}")
            else -> listOf("Late", "was due $time")
        }
        return (parts + listOfNotNull(commitment)).joinToString(", ")
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
