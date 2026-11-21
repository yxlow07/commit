package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.ActiveWorkoutEntity
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.db.entity.WorkoutPhase.COMPLETE
import dev.yxuan.commit.data.db.entity.WorkoutPhase.READY
import dev.yxuan.commit.data.db.entity.WorkoutPhase.REST
import dev.yxuan.commit.data.db.entity.WorkoutPhase.WORK
import kotlin.math.round

/** A set as actually performed (F5.3), to be written to `workout_sets`. */
data class LoggedSet(val exerciseId: String, val setNo: Int, val reps: Int, val weight: Double, val doneAt: Long)

/** Result of an engine step: the new state plus any sets completed along the way. */
data class Step(val state: ActiveWorkoutEntity, val logged: List<LoggedSet> = emptyList())

/**
 * The workout state machine (F5.2–F5.5). Pure: no clock, no Android. Every transition takes `now`,
 * and [resolve] replays any timers that have already ended, so a late alarm or a redraw after
 * a reboot always lands on the right step (PRD "Timers and state").
 *
 * Flow per set: READY (user edits reps/weight, taps "Done set") → REST → next set.
 * Timed exercises: READY ("Start") → WORK (timer) → REST → WORK … hands-free.
 * After an exercise's last set the rest is the routine's longer rest between exercises;
 * after the last exercise the workout is COMPLETE.
 */
class WorkoutEngine(
    private val exercises: List<ExerciseEntity>,
    private val restBetweenExercisesS: Int,
) {
    fun start(sessionId: String, routineId: String, now: Long): ActiveWorkoutEntity {
        val base = ActiveWorkoutEntity(
            sessionId = sessionId, routineId = routineId, exerciseIndex = 0, setNo = 1,
            phase = READY, phaseStartedAt = now, phaseEndsAt = null, reps = 0, weight = 0.0,
        )
        // First set waits for the user even when timed, so a plank doesn't start on "Start workout".
        return enterSet(base, 0, 1, now, autoStartTimed = false, carry = false)
    }

    fun exercise(state: ActiveWorkoutEntity): ExerciseEntity? = exercises.getOrNull(state.exerciseIndex)

    /** Replays every timer that has ended by [now]. */
    fun resolve(state: ActiveWorkoutEntity, now: Long): Step {
        var s = state
        val logged = mutableListOf<LoggedSet>()
        while (true) {
            val endsAt = s.phaseEndsAt ?: break
            if (endsAt > now) break
            s = when (s.phase) {
                WORK -> {
                    logged += log(s, endsAt)
                    afterSet(s, endsAt)
                }
                REST -> afterRest(s, endsAt)
                else -> break
            }
        }
        return Step(s, logged)
    }

    /**
     * Main button: "Done set" logs the set with the edited values; on a timed exercise it is
     * "Start" and begins the work timer.
     */
    fun primary(state: ActiveWorkoutEntity, now: Long): Step {
        if (state.phase != READY) return Step(state)
        val ex = exercise(state) ?: return Step(complete(state, now))
        val duration = ex.durationSeconds
        if (duration != null && duration > 0) {
            return Step(state.copy(phase = WORK, phaseStartedAt = now, phaseEndsAt = now + duration * 1000L))
        }
        return Step(afterSet(state, now), listOf(log(state, now)))
    }

    /** "Skip rest", or end a timed work period early (the set still counts). */
    fun skip(state: ActiveWorkoutEntity, now: Long): Step = when (state.phase) {
        REST -> Step(afterRest(state, now))
        WORK -> Step(afterSet(state, now), listOf(log(state, now)))
        else -> Step(state)
    }

    /** "+30s" on the rest timer. */
    fun addRest(state: ActiveWorkoutEntity, seconds: Int): ActiveWorkoutEntity =
        if (state.phase == REST && state.phaseEndsAt != null) {
            state.copy(phaseEndsAt = state.phaseEndsAt + seconds * 1000L)
        } else state

    fun adjustReps(state: ActiveWorkoutEntity, delta: Int): ActiveWorkoutEntity =
        if (state.phase == READY) state.copy(reps = (state.reps + delta).coerceAtLeast(0)) else state

    /** Weight steps of [delta] kg, never below zero, rounded to 0.25 kg to avoid float drift. */
    fun adjustWeight(state: ActiveWorkoutEntity, delta: Double): ActiveWorkoutEntity =
        if (state.phase == READY) {
            state.copy(weight = (round((state.weight + delta) * 4) / 4).coerceAtLeast(0.0))
        } else state

    // --- transitions ---

    private fun afterSet(s: ActiveWorkoutEntity, at: Long): ActiveWorkoutEntity {
        val ex = exercise(s) ?: return complete(s, at)
        val lastSet = s.setNo >= ex.sets
        val lastExercise = s.exerciseIndex >= exercises.lastIndex
        if (lastSet && lastExercise) return complete(s, at)
        val rest = if (lastSet) restBetweenExercisesS else ex.restSeconds
        if (rest <= 0) return afterRest(s, at)
        return s.copy(phase = REST, phaseStartedAt = at, phaseEndsAt = at + rest * 1000L)
    }

    /** Moves to the next set of this exercise, or the first set of the next one. */
    private fun afterRest(s: ActiveWorkoutEntity, at: Long): ActiveWorkoutEntity {
        val ex = exercise(s) ?: return complete(s, at)
        return if (s.setNo < ex.sets) {
            enterSet(s, s.exerciseIndex, s.setNo + 1, at, autoStartTimed = true, carry = true)
        } else {
            enterSet(s, s.exerciseIndex + 1, 1, at, autoStartTimed = true, carry = false)
        }
    }

    /**
     * Enters a set. Reps/weight start from the exercise's targets on a new exercise, and carry the
     * user's edits to the next set of the same exercise.
     */
    private fun enterSet(
        s: ActiveWorkoutEntity, index: Int, setNo: Int, at: Long, autoStartTimed: Boolean, carry: Boolean,
    ): ActiveWorkoutEntity {
        val ex = exercises.getOrNull(index) ?: return complete(s, at)
        val reps = if (carry) s.reps else ex.reps
        val weight = if (carry) s.weight else ex.weight
        val duration = ex.durationSeconds
        val timed = duration != null && duration > 0
        return s.copy(
            exerciseIndex = index,
            setNo = setNo,
            phase = if (timed && autoStartTimed) WORK else READY,
            phaseStartedAt = at,
            phaseEndsAt = if (timed && autoStartTimed) at + duration!! * 1000L else null,
            reps = reps,
            weight = weight,
        )
    }

    private fun complete(s: ActiveWorkoutEntity, at: Long) =
        s.copy(phase = COMPLETE, phaseStartedAt = at, phaseEndsAt = null)

    private fun log(s: ActiveWorkoutEntity, at: Long): LoggedSet {
        val ex = exercise(s)!!
        val timed = (ex.durationSeconds ?: 0) > 0
        return LoggedSet(ex.id, s.setNo, if (timed) 0 else s.reps, if (timed) 0.0 else s.weight, at)
    }

    companion object {
        const val WEIGHT_STEP_KG = 2.5
        const val ADD_REST_SECONDS = 30

        /** Training volume: sum of reps × weight (F6.2, F8.4). */
        fun volume(sets: List<Pair<Int, Double>>): Double = sets.sumOf { (reps, weight) -> reps * weight }

        fun isTimer(phase: WorkoutPhase) = phase == WORK || phase == REST
    }
}
