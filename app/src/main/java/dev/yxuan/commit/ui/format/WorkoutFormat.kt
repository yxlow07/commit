package dev.yxuan.commit.ui.format

import dev.yxuan.commit.data.repo.LiveWorkout
import java.text.DecimalFormat
import kotlin.math.max

/** Shared text for the workout on the widget, notification and app. */
object WorkoutFormat {
    private val weightFormat = DecimalFormat("0.##")
    private val volumeFormat = DecimalFormat("#,##0")

    fun weight(kg: Double): String = "${weightFormat.format(kg)} kg"

    /** The number alone, for steppers ("62.5"); the unit goes under it. */
    fun weightValue(kg: Double): String = weightFormat.format(kg)

    /** "Push Day, 2nd exercise": the routine and which exercise this is. */
    fun position(live: LiveWorkout): String =
        "${live.routine.name}, ${ordinal(live.state.exerciseIndex + 1)} exercise"

    /** 1st, 2nd, 3rd, 4th … 11th, 12th, 13th … 21st, 22nd. */
    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    /** What the current rest leads to: "set 4", or the next exercise's name. */
    fun restThen(live: LiveWorkout): String? {
        live.exercise ?: return null
        val i = live.state.exerciseIndex
        return if (live.remainingOf(i) > 0) "set ${live.doneOf(i) + 1}" else live.nextExercise?.name
    }

    /** "Next: Bench press, set 4 of 4" for the workout notification. */
    fun nextLine(live: LiveWorkout): String? {
        val ex = live.exercise ?: return null
        val i = live.state.exerciseIndex
        if (live.remainingOf(i) > 0) return "Next: ${ex.name}, set ${live.doneOf(i) + 1} of ${ex.sets}"
        val next = live.nextExercise ?: return null
        return "Next: ${next.name}, set ${(live.doneCounts[next.id] ?: 0) + 1} of ${next.sets}"
    }

    /** "52 min" from start to the last change. */
    fun duration(live: LiveWorkout): String = "${max(1L, (live.state.phaseStartedAt - live.startedAt) / 60_000)} min"

    /** "Set 2 of 3"; past the planned sets, "Extra set", then "Extra set 2". */
    fun setOf(setNo: Int, sets: Int): String = when {
        setNo <= sets -> "Set $setNo of $sets"
        setNo == sets + 1 -> "Extra set"
        else -> "Extra set ${setNo - sets}"
    }

    fun setProgress(live: LiveWorkout): String {
        val ex = live.exercise ?: return ""
        return setOf(live.state.setNo, ex.sets)
    }

    /** "Set 2 of 4, 8 × 60 kg", or "Set 1 of 2, 30 s" for timed exercises. */
    fun setLine(live: LiveWorkout): String {
        val ex = live.exercise ?: return ""
        val what = if (live.isTimed) "${ex.durationSeconds} s" else "${live.state.reps} × ${weight(live.state.weight)}"
        return "${setProgress(live)}, $what"
    }

    /** "42 min, 3,450 kg, 12 sets" (F6.2 workout summary). */
    fun summary(live: LiveWorkout): String {
        val end = live.state.phaseStartedAt
        val minutes = max(1L, (end - live.startedAt) / 60_000)
        return "$minutes min, ${volume(live.volume)}, ${live.doneSets.size} ${if (live.doneSets.size == 1) "set" else "sets"}"
    }

    /** "52 min, 16 sets" for a finished session. */
    fun sessionMeta(s: dev.yxuan.commit.data.db.dao.SessionSummary): String {
        val minutes = max(1L, ((s.session.endedAt ?: s.session.startedAt) - s.session.startedAt) / 60_000)
        return "$minutes min, ${s.setCount} ${if (s.setCount == 1) "set" else "sets"}"
    }

    /** "Thu 20 Nov". */
    fun sessionDate(s: dev.yxuan.commit.data.db.dao.SessionSummary, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(s.session.startedAt).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))

    /** Start time in the phone's 12/24-hour style, e.g. "6:30 PM", to tell same-day sessions apart. */
    fun sessionTime(s: dev.yxuan.commit.data.db.dao.SessionSummary, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(s.session.startedAt).atZone(zone).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

    /** Training volume, e.g. "3,450 kg". */
    fun volume(kg: Double): String = "${volumeFormat.format(kg)} kg"

    /** m:ss for in-app countdowns. */
    fun countdown(millis: Long): String {
        val total = max(0L, (millis + 999) / 1000)
        return "%d:%02d".format(total / 60, total % 60)
    }
}
