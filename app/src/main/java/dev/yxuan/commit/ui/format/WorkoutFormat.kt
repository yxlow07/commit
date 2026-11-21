package dev.yxuan.commit.ui.format

import dev.yxuan.commit.data.repo.LiveWorkout
import java.text.DecimalFormat
import kotlin.math.max

/** Shared text for the workout on the widget, notification and app. */
object WorkoutFormat {
    private val weightFormat = DecimalFormat("0.##")
    private val volumeFormat = DecimalFormat("#,##0")

    fun weight(kg: Double): String = "${weightFormat.format(kg)} kg"

    fun setProgress(live: LiveWorkout): String {
        val ex = live.exercise ?: return ""
        return "Set ${live.state.setNo} of ${ex.sets}"
    }

    /** "Set 2 of 4 · 8 × 60 kg", or "Set 1 of 2 · 30 s" for timed exercises. */
    fun setLine(live: LiveWorkout): String {
        val ex = live.exercise ?: return ""
        val what = if (live.isTimed) "${ex.durationSeconds} s" else "${live.state.reps} × ${weight(live.state.weight)}"
        return "${setProgress(live)} · $what"
    }

    /** "42 min · 3,450 kg · 12 sets" (F6.2 workout summary). */
    fun summary(live: LiveWorkout): String {
        val end = live.state.phaseStartedAt
        val minutes = max(1L, (end - live.startedAt) / 60_000)
        return "$minutes min · ${volume(live.volume)} · ${live.sets.size} sets"
    }

    /** Training volume, e.g. "3,450 kg". */
    fun volume(kg: Double): String = "${volumeFormat.format(kg)} kg"

    /** m:ss for in-app countdowns. */
    fun countdown(millis: Long): String {
        val total = max(0L, (millis + 999) / 1000)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
