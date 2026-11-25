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
        // The label comes from the sets logged so far, as stored before the rest begins.
        val logged = mutableListOf<dev.yxuan.commit.data.db.entity.WorkoutSetEntity>()
        fun keep(step: Step): ActiveWorkoutEntity {
            step.logged.forEach {
                logged += dev.yxuan.commit.data.db.entity.WorkoutSetEntity(
                    id = "${it.exerciseId}-${it.setNo}", updatedAt = 0, sessionId = "session", exerciseId = it.exerciseId,
                    setNo = it.setNo, reps = it.reps, weight = it.weight, doneAt = it.doneAt,
                )
            }
            return step.state
        }
        fun live(st: ActiveWorkoutEntity) = LiveWorkout(st, routine, listOf(bench, plank), logged.toList(), t0)
        var st = engine.start("session", "r", t0)
        st = keep(engine.primary(st, s(0))) // bench set 1 done -> rest
        assertEquals("Next set", live(st).nextLabel)
        assertEquals("bench, set 2", live(st).upNext)
        st = keep(engine.primary(engine.skip(st, s(10)).state, s(20))) // bench set 2 (last) done -> rest
        assertEquals(REST, st.phase)
        assertEquals("Next exercise", live(st).nextLabel)
        assertEquals("plank", live(st).upNext)
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

    // ---- Choosing another exercise ----

    private val squat = exercise("squat", sets = 1, rest = 45, order = 2)
    private val three = WorkoutEngine(listOf(bench, plank, squat), restBetweenExercisesS = 120)

    @Test
    fun chooseJumpsToAnotherExerciseReady() {
        val st = three.start("session", "r", t0)
        val jumped = three.choose(st, 2, s(5)).state
        assertEquals(2, jumped.exerciseIndex)
        assertEquals(1, jumped.setNo)
        assertEquals(READY, jumped.phase)
        // Timed exercises wait for Start when chosen, too.
        val plankChosen = three.choose(st, 1, s(5)).state
        assertEquals(READY, plankChosen.phase)
        assertNull(plankChosen.phaseEndsAt)
    }

    @Test
    fun chooseDuringRestKeepsProgressOfTheExerciseLeft() {
        var st = three.start("session", "r", t0)
        st = three.primary(st, s(10)).state // bench set 1 logged, resting
        assertEquals(REST, st.phase)
        st = three.choose(st, 2, s(20)).state // squat instead
        assertEquals(2, st.exerciseIndex)
        val afterSquat = three.primary(st, s(30))
        assertEquals("squat", afterSquat.logged.single().exerciseId)
        // Squat is finished, so after the rest the workout returns to bench at set 2, not plank.
        val next = three.skip(afterSquat.state, s(40)).state
        assertEquals(0, next.exerciseIndex)
        assertEquals(2, next.setNo)
    }

    @Test
    fun cannotChooseAFinishedExerciseOrSwitchMidHold() {
        var st = three.start("session", "r", t0)
        st = three.choose(st, 2, s(1)).state
        st = three.primary(st, s(2)).state // squat done (1 set)
        assertEquals(st, three.choose(st, 2, s(3)).state)
        val work = st.copy(phase = WORK, phaseEndsAt = s(60))
        assertEquals(work, three.choose(work, 0, s(4)).state)
    }

    @Test
    fun completesOnlyWhenEveryExerciseIsDone() {
        val engine2 = WorkoutEngine(listOf(bench, squat), restBetweenExercisesS = 0, done = mapOf("bench" to 2))
        // Bench was finished earlier in the session; doing the squat ends the workout.
        val st = engine2.start("session", "r", t0).copy(exerciseIndex = 1, setNo = 1, reps = 8)
        assertEquals(COMPLETE, engine2.primary(st, s(5)).state.phase)
        // With bench not done, finishing squat (the last in order) wraps back to bench instead.
        val engine3 = WorkoutEngine(listOf(bench, squat), restBetweenExercisesS = 0)
        val st3 = engine3.choose(engine3.start("session", "r", t0), 1, s(1)).state
        val after = engine3.primary(st3, s(5)).state
        assertEquals(READY, after.phase)
        assertEquals(0, after.exerciseIndex)
    }

    @Test
    fun oneMoreSetAfterTheLastSetKeepsValuesThenRestsBetweenExercises() {
        var st = engine.start("session", "r", t0)
        st = engine.adjustWeight(st, 5.0)
        st = engine.primary(st, s(10)).state // bench 1
        st = engine.skip(st, s(20)).state
        st = engine.primary(st, s(30)).state // bench 2: last planned set
        assertEquals(REST, st.phase)
        assertEquals(s(150), st.phaseEndsAt) // the between-exercises rest
        val extra = engine.extraSet(st, s(40)).state
        assertEquals(READY, extra.phase)
        assertEquals(0, extra.exerciseIndex)
        assertEquals(3, extra.setNo)
        assertEquals(65.0, extra.weight, 0.0)
        val logged = engine.primary(extra, s(50))
        assertEquals(listOf(LoggedSet("bench", 3, 8, 65.0, s(50))), logged.logged)
        // Still the longer rest before the plank, and another extra set is still offered.
        assertEquals(REST, logged.state.phase)
        assertEquals(s(170), logged.state.phaseEndsAt)
        assertEquals(4, engine.extraSet(logged.state, s(55)).state.setNo)
    }

    @Test
    fun noExtraSetWhileTheExerciseHasPlannedSetsLeftOrMidSet() {
        val st = engine.start("session", "r", t0)
        assertEquals(st, engine.extraSet(st, s(1)).state) // READY
        val resting = engine.primary(st, s(10)).state // bench 1 of 2
        assertEquals(resting, engine.extraSet(resting, s(11)).state)
    }

    @Test
    fun oneMoreSetAfterTheWorkoutIsDone() {
        val one = WorkoutEngine(listOf(bench), restBetweenExercisesS = 0, done = mapOf("bench" to 1))
        val st = one.start("session", "r", t0).copy(setNo = 2)
        val done = one.primary(st, s(5)).state
        assertEquals(COMPLETE, done.phase)
        val extra = one.extraSet(done, s(10)).state
        assertEquals(READY, extra.phase)
        assertEquals(3, extra.setNo)
        assertEquals(COMPLETE, one.primary(extra, s(20)).state.phase)
    }

    @Test
    fun skipSetRecordsASkippedSetAndGoesStraightToTheNextSet() {
        var st = engine.start("session", "r", t0)
        st = engine.adjustWeight(st, 5.0)
        val step = engine.skipSet(st, s(10))
        assertEquals(listOf(LoggedSet("bench", 1, 0, 0.0, s(10), skipped = true)), step.logged)
        assertEquals(READY, step.state.phase) // no rest after a skip
        assertEquals(0, step.state.exerciseIndex)
        assertEquals(2, step.state.setNo)
        assertEquals(65.0, step.state.weight, 0.0) // edits carry
        // Skipping bench's last set moves to the plank, ready (not auto-started).
        val next = engine.skipSet(step.state, s(20)).state
        assertEquals(READY, next.phase)
        assertEquals(1, next.exerciseIndex)
        assertEquals(1, next.setNo)
    }

    @Test
    fun endExerciseSkipsTheSetsLeftAndStartsTheNextExercise() {
        var st = engine.start("session", "r", t0)
        st = engine.primary(st, s(10)).state // bench 1 of 2, now resting
        val step = engine.endExercise(st, s(20))
        assertEquals(listOf(LoggedSet("bench", 2, 0, 0.0, s(20), skipped = true)), step.logged)
        assertEquals(READY, step.state.phase)
        assertEquals(1, step.state.exerciseIndex)
        assertNull(step.state.phaseEndsAt)
        // Ending the last exercise finishes the workout.
        val end = engine.endExercise(step.state, s(30))
        assertEquals(2, end.logged.size)
        assertEquals(COMPLETE, end.state.phase)
    }

    @Test
    fun skippedSetsCountAsDealtWithButNotAsDone() {
        val sets = listOf(
            dev.yxuan.commit.data.db.entity.WorkoutSetEntity(id = "a", updatedAt = 0, sessionId = "x", exerciseId = "bench", setNo = 1, reps = 8, weight = 60.0, doneAt = 0),
            dev.yxuan.commit.data.db.entity.WorkoutSetEntity(id = "b", updatedAt = 0, sessionId = "x", exerciseId = "bench", setNo = 2, reps = 0, weight = 0.0, doneAt = 0, skipped = true),
        )
        assertEquals(mapOf("bench" to 2), WorkoutEngine.doneCounts(sets))
    }
}
