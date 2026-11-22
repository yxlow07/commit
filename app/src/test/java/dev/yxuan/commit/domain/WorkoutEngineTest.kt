package dev.yxuan.commit.domain

import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.data.db.entity.ActiveWorkoutEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
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
    fun endedRestWaitsForNextSetThenCarriesEdits() {
        var st = engine.start("session", "r", t0)
        st = engine.adjustWeight(st, 5.0)
        st = engine.primary(st, s(10)).state
        // The rest ran out long ago: still resting (overtime) until the user taps Next set.
        val waiting = engine.resolve(st, s(500)).state
        assertEquals(st, waiting)
        val next = engine.skip(waiting, s(500)).state
        assertEquals(READY, next.phase)
        assertEquals(2, next.setNo)
        assertEquals(65.0, next.weight, 0.0)
        // The new set's timer starts when Next set is tapped.
        assertEquals(s(500), next.phaseStartedAt)
    }

    @Test
    fun lastSetUsesRestBetweenExercisesThenTimedExerciseRunsHandsFree() {
        var st = engine.start("session", "r", t0)
        st = engine.skip(engine.resolve(engine.primary(st, s(0)).state, s(60)).state, s(60)).state // set 1 + rest + Next set
        val afterLast = engine.primary(st, s(100))
        assertEquals(REST, afterLast.state.phase)
        assertEquals(s(220), afterLast.state.phaseEndsAt) // 120 s between exercises

        // Rest ends and waits; Next set starts the plank's work timer by itself (F5.5).
        assertEquals(REST, engine.resolve(afterLast.state, s(300)).state.phase)
        val work = engine.skip(afterLast.state, s(220)).state
        assertEquals(WORK, work.phase)
        assertEquals(1, work.exerciseIndex)
        assertEquals(s(260), work.phaseEndsAt)

        // Work ends -> set logged at its exact end, then the 30 s rest; Next set -> set 2 -> complete.
        val rest = engine.resolve(work, s(1_000))
        assertEquals(REST, rest.state.phase)
        assertEquals(listOf(LoggedSet("plank", 1, 0, 0.0, s(260))), rest.logged)
        val set2 = engine.skip(rest.state, s(1_000)).state
        val done = engine.resolve(set2, s(10_000))
        assertEquals(COMPLETE, done.state.phase)
        assertEquals(listOf(LoggedSet("plank", 2, 0, 0.0, s(1_040))), done.logged)
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
    fun restButtonSaysNextExerciseAfterTheLastSet() {
        val routine = RoutineEntity(id = "r", updatedAt = 0, commitmentId = "c", name = "Push Day", sortOrder = 0)
        fun live(st: ActiveWorkoutEntity) = LiveWorkout(st, routine, listOf(bench, plank), emptyList(), t0)
        var st = engine.start("session", "r", t0)
        st = engine.primary(st, s(0)).state // bench set 1 done -> rest
        assertEquals("Next set", live(st).nextLabel)
        st = engine.primary(engine.skip(st, s(10)).state, s(20)).state // bench set 2 (last) done -> rest
        assertEquals(REST, st.phase)
        assertEquals("Next exercise", live(st).nextLabel)
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
