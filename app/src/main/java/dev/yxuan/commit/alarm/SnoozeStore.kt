package dev.yxuan.commit.alarm

import android.content.Context

/**
 * Snoozed todo reminders (F7.4), phone-only and not synced. Kept outside the alarm itself so that
 * [AlarmScheduler.rescheduleTodos] (after every change, sync and boot) re-sets them instead of
 * cancelling them. A snooze is tied to the todo's due time when it was snoozed: editing the due time
 * drops it, and so do ticking or deleting the todo, or the snoozed reminder firing.
 */
class SnoozeStore(context: Context) {
    private val prefs = context.getSharedPreferences("snoozes", Context.MODE_PRIVATE)

    data class Snooze(val until: Long, val dueAt: Long)

    fun get(todoId: String): Snooze? {
        val raw = prefs.getString(todoId, null) ?: return null
        val parts = raw.split('|')
        val until = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val dueAt = parts.getOrNull(1)?.toLongOrNull() ?: return null
        return Snooze(until, dueAt)
    }

    fun put(todoId: String, snooze: Snooze) {
        prefs.edit().putString(todoId, "${snooze.until}|${snooze.dueAt}").apply()
    }

    fun remove(todoId: String) {
        if (prefs.contains(todoId)) prefs.edit().remove(todoId).apply()
    }

    fun ids(): Set<String> = prefs.all.keys.toSet()
}
