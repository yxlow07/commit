package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.ActiveWorkoutEntity
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.db.entity.WorkoutPhase.COMPLETE
import dev.yxuan.commit.data.db.entity.WorkoutPhase.READY
import dev.yxuan.commit.data.db.entity.WorkoutPhase.REST
import dev.yxuan.commit.data.db.entity.WorkoutPhase.WORK
import kotlin.math.round

/**
 * A set as actually performed (F5.3), to be written to `workout_sets`. A [skipped] set ("Skip set",
 * "End exercise") has no reps or weight: it only marks the set as dealt with, so it isn't offered again.
 */
data class LoggedSet(val exerciseId: String, val setNo: Int, val reps: Int, val weight: Double, val doneAt: Long, val skipped: Boolean = false)

/** Result of an engine step: the new state plus any sets completed along the way. */
data class Step(val state: ActiveWorkoutEntity, val logged: List<LoggedSet> = emptyList())

/**
 * The workout state machine (F5.2–F5.5). Pure: no clock, no Android. Every transition takes `now`,
 * and [resolve] replays any timers that have already ended, so a late alarm or a redraw after
 * a reboot always lands on the right step (PRD "Timers and state").
 *
 * Flow per set: READY (user edits reps/weight, taps "Done set") → REST → "Next set" → next set.
 * A rest that runs out stays in REST (overtime, counted up in red) until the user taps Next set.
 * Timed exercises: READY ("Start") → WORK (timer) → REST → Next set → WORK.
 * After an exercise's last set the rest is the routine's longer rest between exercises, then the
 * next exercise that still has sets left (so exercises can be done in any order via [choose]);
 * once every exercise is finished the workout is COMPLETE.
 *
 * [done] is how many sets of each exercise (by id) this session has already dealt with: logged or
 * skipped ([doneCounts]).
 */
class WorkoutEngine(
    private val exercises: List<ExerciseEntity>,
    private val restBetweenExercisesS: Int,
    done: Map<String, Int> = emptyMap(),
) {
    /** Sets logged per exercise, including those logged by this engine's own steps. */
    private val doneCounts = done.toMutableMap()

    private fun doneOf(index: Int): Int = exercises.getOrNull(index)?.let { doneCounts[it.id] ?: 0 } ?: 0
    private fun remaining(index: Int): Int = exercises.getOrNull(index)?.let { it.sets - doneOf(index) } ?: 0

    /** The next exercise after [current] (wrapping round) that still has sets left, or null when all are done. */
    private fun nextIndex(current: Int): Int? =
        (1..exercises.size).map { (current + it) % exercises.size }.firstOrNull { remaining(it) > 0 }
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
                // An ended rest waits for "Next set" (overtime); it never moves on by itself.
                REST -> break
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
        val logged = log(state, now) // first: the count it adds decides what comes next
        return Step(afterSet(state, now), listOf(logged))
    }

    /**
     * Switch to another exercise (the user picks what to do next): allowed between sets (READY) and
     * during a rest, for any exercise with sets left. Its next set starts ready, timed ones included.
     */
    fun choose(state: ActiveWorkoutEntity, index: Int, now: Long): Step {
        if (state.phase != READY && state.phase != REST) return Step(state)
        if (remaining(index) <= 0) return Step(state)
        if (index == state.exerciseIndex && state.phase == READY) return Step(state)
        return Step(enterSet(state, index, doneOf(index) + 1, now, autoStartTimed = false, carry = false))
    }

    /**
     * "One more set": after an exercise's planned sets (in the rest that follows, or once the workout
     * is done), adds another set of the same exercise, keeping the last reps and weight. It waits
     * READY (timed ones too). Logging it leads to the between-exercises rest again, or COMPLETE.
     */
    fun extraSet(state: ActiveWorkoutEntity, now: Long): Step {
        if (state.phase != REST && state.phase != COMPLETE) return Step(state)
        val i = state.exerciseIndex
        if (exercises.getOrNull(i) == null || remaining(i) > 0) return Step(state)
        return Step(enterSet(state, i, doneOf(i) + 1, now, autoStartTimed = false, carry = true))
    }

    /**
     * "Skip set": the set waiting (READY) is passed over without logging reps, and the next one starts
     * at once, no rest: the next set of this exercise, else the next exercise with sets left, else done.
     */
    fun skipSet(state: ActiveWorkoutEntity, now: Long): Step {
        if (state.phase != READY) return Step(state)
        exercise(state) ?: return Step(state)
        val skipped = skip(state, state.setNo, now)
        return Step(moveOn(state, now), listOf(skipped))
    }

    /**
     * "End exercise": the exercise's sets that are left are skipped (between sets or during a rest),
     * and the next exercise with sets left starts ready, or the workout is done.
     */
    fun endExercise(state: ActiveWorkoutEntity, now: Long): Step {
        if (state.phase != READY && state.phase != REST) return Step(state)
        val ex = exercise(state) ?: return Step(state)
        val first = doneOf(state.exerciseIndex) + 1
        val skipped = (first..ex.sets).map { skip(state, it, now) }
        val next = nextIndex(state.exerciseIndex) ?: return Step(complete(state, now), skipped)
        return Step(enterSet(state, next, doneOf(next) + 1, now, autoStartTimed = false, carry = false), skipped)
    }

    /** "Skip rest" / "Next set" (also in overtime), or end a timed work period early (the set still counts). */
    fun skip(state: ActiveWorkoutEntity, now: Long): Step = when (state.phase) {
        REST -> Step(afterRest(state, now))
        WORK -> log(state, now).let { Step(afterSet(state, now), listOf(it)) }
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

    /** After a set is logged: rest (longer between exercises), or COMPLETE when nothing is left. */
    private fun afterSet(s: ActiveWorkoutEntity, at: Long): ActiveWorkoutEntity {
        val ex = exercise(s) ?: return complete(s, at)
        val lastSet = remaining(s.exerciseIndex) <= 0
        if (lastSet && nextIndex(s.exerciseIndex) == null) return complete(s, at)
        val rest = if (lastSet) restBetweenExercisesS else ex.restSeconds
        if (rest <= 0) return afterRest(s, at)
        return s.copy(phase = REST, phaseStartedAt = at, phaseEndsAt = at + rest * 1000L)
    }

    /** Moves to the next set of this exercise, or the first unfinished set of the next exercise with sets left. */
    private fun afterRest(s: ActiveWorkoutEntity, at: Long): ActiveWorkoutEntity {
        exercise(s) ?: return complete(s, at)
        if (remaining(s.exerciseIndex) > 0) {
            return enterSet(s, s.exerciseIndex, doneOf(s.exerciseIndex) + 1, at, autoStartTimed = true, carry = true)
        }
        val next = nextIndex(s.exerciseIndex) ?: return complete(s, at)
        return enterSet(s, next, doneOf(next) + 1, at, autoStartTimed = true, carry = false)
    }

    /** After a skipped set: straight to the next set of this exercise (ready), else the next exercise, else done. */
    private fun moveOn(s: ActiveWorkoutEntity, at: Long): ActiveWorkoutEntity {
        if (remaining(s.exerciseIndex) > 0) {
            return enterSet(s, s.exerciseIndex, doneOf(s.exerciseIndex) + 1, at, autoStartTimed = false, carry = true)
        }
        val next = nextIndex(s.exerciseIndex) ?: return complete(s, at)
        return enterSet(s, next, doneOf(next) + 1, at, autoStartTimed = false, carry = false)
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

    /** Logs the current set and counts it, so the next step knows what is left. */
    private fun log(s: ActiveWorkoutEntity, at: Long): LoggedSet {
        val ex = exercise(s)!!
        val timed = (ex.durationSeconds ?: 0) > 0
        doneCounts[ex.id] = (doneCounts[ex.id] ?: 0) + 1
        return LoggedSet(ex.id, s.setNo, if (timed) 0 else s.reps, if (timed) 0.0 else s.weight, at)
    }

    /** Records set [setNo] of the current exercise as skipped and counts it as dealt with. */
    private fun skip(s: ActiveWorkoutEntity, setNo: Int, at: Long): LoggedSet {
        val ex = exercise(s)!!
        doneCounts[ex.id] = (doneCounts[ex.id] ?: 0) + 1
        return LoggedSet(ex.id, setNo, 0, 0.0, at, skipped = true)
    }

    companion object {
        const val WEIGHT_STEP_KG = 2.5
        const val ADD_REST_SECONDS = 30

        /** Training volume: sum of reps × weight (F6.2, F8.4). */
        fun volume(sets: List<Pair<Int, Double>>): Double = sets.sumOf { (reps, weight) -> reps * weight }

        fun isTimer(phase: WorkoutPhase) = phase == WORK || phase == REST

        /**
         * Sets dealt with per exercise id: logged or skipped, so a skipped set isn't offered again.
         * Totals and History count only the sets actually done (not skipped).
         */
        fun doneCounts(sets: List<dev.yxuan.commit.data.db.entity.WorkoutSetEntity>): Map<String, Int> =
            sets.filter { !it.deleted }.groupingBy { it.exerciseId }.eachCount()
    }
}
