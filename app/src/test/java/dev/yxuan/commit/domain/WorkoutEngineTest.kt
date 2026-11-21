package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.WorkoutPhase.COMPLETE
import dev.yxuan.commit.data.db.entity.WorkoutPhase.READY
import dev.yxuan.commit.data.db.entity.WorkoutPhase.REST
import dev.yxuan.commit.data.db.entity.WorkoutPhase.WORK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEngineTest {
    private fun exercise(id: String, sets: Int, reps: Int = 8, weight: Double = 60.0, rest: Int = 60, duration: Int? = null, order: Int = 0) =
        ExerciseEntity(
            id = id, updatedAt = 0, routineId = "r", name = id, sets = sets, reps = reps,
            weight = weight, restSeconds = rest, durationSeconds = duration, sortOrder = order,
        )

    private val bench = exercise("bench", sets = 2, reps = 8, weight = 60.0, rest = 60)
    private val plank = exercise("plank", sets = 2, rest = 30, duration = 40, order = 1)
    private val engine = WorkoutEngine(listOf(bench, plank), restBetweenExercisesS = 120)
    private val t0 = 1_000_000L
    private fun s(seconds: Int) = t0 + seconds * 1000L

    @Test
    fun startPrefillsFirstExerciseTargets() {
        val st = engine.start("session", "r", t0)
        assertEquals(READY, st.phase)
        assertEquals(0, st.exerciseIndex)
        assertEquals(1, st.setNo)
        assertEquals(8, st.reps)
        assertEquals(60.0, st.weight, 0.0)
        assertNull(st.phaseEndsAt)
    }

    @Test
    fun doneSetLogsEditedValuesAndStartsRest() {
        var st = engine.start("session", "r", t0)
        st = engine.adjustReps(st, -2)
        st = engine.adjustWeight(st, 2.5)
        val step = engine.primary(st, s(10))
        assertEquals(listOf(LoggedSet("bench", 1, 6, 62.5, s(10))), step.logged)
        assertEquals(REST, step.state.phase)
        assertEquals(s(70), step.state.phaseEndsAt)
    }

    @Test
    fun restEndAdvancesToNextSetAndCarriesEdits() {
        var st = engine.start("session", "r", t0)
        st = engine.adjustWeight(st, 5.0)
        st = engine.primary(st, s(10)).state
        val resolved = engine.resolve(st, s(70))
        assertEquals(READY, resolved.state.phase)
        assertEquals(2, resolved.state.setNo)
        assertEquals(65.0, resolved.state.weight, 0.0)
        // Not yet ended: nothing happens.
        assertEquals(st, engine.resolve(st, s(69)).state)
    }

    @Test
    fun lastSetUsesRestBetweenExercisesThenTimedExerciseRunsHandsFree() {
        var st = engine.start("session", "r", t0)
        st = engine.resolve(engine.primary(st, s(0)).state, s(60)).state // set 1 + rest
        val afterLast = engine.primary(st, s(100))
        assertEquals(REST, afterLast.state.phase)
        assertEquals(s(220), afterLast.state.phaseEndsAt) // 120 s between exercises

        // Rest ends -> plank work starts by itself (F5.5), then rest, then set 2, then complete.
        val work = engine.resolve(afterLast.state, s(220)).state
        assertEquals(WORK, work.phase)
        assertEquals(1, work.exerciseIndex)
        assertEquals(s(260), work.phaseEndsAt)

        val done = engine.resolve(work, s(10_000))
        assertEquals(COMPLETE, done.state.phase)
        // Both plank sets logged at their exact end times, as timed sets (no reps/weight).
        assertEquals(listOf(LoggedSet("plank", 1, 0, 0.0, s(260)), LoggedSet("plank", 2, 0, 0.0, s(330))), done.logged)
    }

    @Test
    fun lateResolveReplaysEveryEndedTimer() {
        // Phone asleep through rest and both timed sets: one resolve lands on the right step.
        val timedOnly = WorkoutEngine(listOf(plank.copy(sortOrder = 0)), restBetweenExercisesS = 0)
        val st = timedOnly.start("session", "r", t0)
        assertEquals(READY, st.phase) // first timed set waits for "Start"
        val running = timedOnly.primary(st, s(0)).state
        assertEquals(WORK, running.phase)
        val mid = timedOnly.resolve(running, s(50)) // 40 s work done, 10 s into 30 s rest
        assertEquals(REST, mid.state.phase)
        assertEquals(1, mid.logged.size)
        assertEquals(s(70), mid.state.phaseEndsAt)
    }

    @Test
    fun skipAndAddRest() {
        var st = engine.start("session", "r", t0)
        st = engine.primary(st, s(0)).state
        st = engine.addRest(st, 30)
        assertEquals(s(90), st.phaseEndsAt)
        val skipped = engine.skip(st, s(5)).state
        assertEquals(READY, skipped.phase)
        assertEquals(2, skipped.setNo)
        // Steppers only change the set being prepared.
        assertEquals(st, engine.adjustReps(st, 1))
    }

    @Test
    fun weightNeverNegativeAndRepsNeverBelowZero() {
        var st = engine.start("session", "r", t0).copy(weight = 2.5, reps = 1)
        st = engine.adjustWeight(st, -2.5)
        st = engine.adjustWeight(st, -2.5)
        st = engine.adjustReps(st, -1)
        st = engine.adjustReps(st, -1)
        assertEquals(0.0, st.weight, 0.0)
        assertEquals(0, st.reps)
    }

    @Test
    fun singleSetSingleExerciseCompletesImmediately() {
        val one = WorkoutEngine(listOf(exercise("curl", sets = 1)), restBetweenExercisesS = 90)
        val step = one.primary(one.start("session", "r", t0), s(30))
        assertEquals(COMPLETE, step.state.phase)
        assertEquals(1, step.logged.size)
        assertTrue(WorkoutEngine.volume(step.logged.map { it.reps to it.weight }) == 8 * 60.0)
    }
}
