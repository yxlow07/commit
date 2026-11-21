package dev.yxuan.commit.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.DataChangeListener
import dev.yxuan.commit.data.repo.ExerciseInput
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.data.repo.RoutineRepository
import dev.yxuan.commit.data.repo.WorkoutEffects
import dev.yxuan.commit.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Clock the test moves forward by hand. */
private class TestClock(var now: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
}

private class RecordingEffects : WorkoutEffects {
    val timers = mutableListOf<Long?>()
    var alerts = 0
    var last: LiveWorkout? = null
    override fun setTimer(at: Long?) { timers += at }
    override suspend fun changed(live: LiveWorkout?) { last = live }
    override fun alert(live: LiveWorkout) { alerts++ }
}

@RunWith(AndroidJUnit4::class)
class WorkoutTest {
    private lateinit var db: AppDatabase
    private val clock = TestClock(1_800_000_000_000)
    private val effects = RecordingEffects()
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var commitments: CommitmentRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        val noop = DataChangeListener { }
        commitments = CommitmentRepository(db.commitmentDao(), clock, noop)
        routines = RoutineRepository(db, clock, noop)
        workouts = WorkoutRepository(db, clock, effects)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun fullWorkoutLogsActualValuesAndEndsSession() = runTest {
        val gym = commitments.create("Gym", CommitmentType.WORKOUT, 0)
        val push = routines.createRoutine(gym, "Push Day")
        routines.updateRoutine(push, "Push Day", restBetweenExercisesS = 120)
        routines.saveExercise(push, null, ExerciseInput("Bench", sets = 2, reps = 8, weight = 60.0, restSeconds = 60, durationSeconds = null))
        routines.saveExercise(push, null, ExerciseInput("Plank", sets = 1, reps = 0, weight = 0.0, restSeconds = 30, durationSeconds = 40))

        val start = clock.now
        workouts.start(push)
        var live = workouts.getLive()!!
        assertEquals("Bench", live.exercise?.name)
        assertEquals(WorkoutPhase.READY, live.state.phase)

        workouts.stepWeight(+1) // 62.5 kg
        workouts.stepReps(-1) // 7 reps
        clock.now += 30_000
        workouts.primary() // done set 1 -> 60 s rest
        live = workouts.getLive()!!
        assertEquals(WorkoutPhase.REST, live.state.phase)
        assertEquals(clock.now + 60_000, effects.timers.last())

        clock.now += 60_000
        workouts.onTimer() // rest over -> set 2, alert
        assertEquals(1, effects.alerts)
        live = workouts.getLive()!!
        assertEquals(2, live.state.setNo)
        assertEquals(62.5, live.state.weight, 0.0) // edits carried over
        assertNull(effects.timers.last())

        clock.now += 30_000
        workouts.primary() // last bench set -> 120 s rest between exercises
        assertEquals(clock.now + 120_000, effects.timers.last())

        // Phone asleep: one late timer replays rest -> plank work -> complete.
        clock.now += 200_000
        workouts.onTimer()
        live = workouts.getLive()!!
        assertEquals(WorkoutPhase.COMPLETE, live.state.phase)

        val sets = db.workoutSetDao().getForSession(live.state.sessionId)
        assertEquals(listOf(7 to 62.5, 7 to 62.5, 0 to 0.0), sets.map { it.reps to it.weight })
        val session = db.workoutSessionDao().getById(live.state.sessionId)!!
        assertEquals(start, session.startedAt)
        assertNotNull(session.endedAt)

        workouts.end() // dismiss summary
        assertNull(workouts.getLive())
        assertNull(effects.last)
        assertTrue(db.workoutSessionDao().observeAll().first().isNotEmpty())
    }

    @Test
    fun endingWithoutSetsDiscardsSession() = runTest {
        val gym = commitments.create("Gym", CommitmentType.WORKOUT, 0)
        val legs = routines.createRoutine(gym, "Legs")
        routines.saveExercise(legs, null, ExerciseInput("Squat", 3, 5, 100.0, 120, null))
        workouts.start(legs)
        workouts.end()
        assertNull(workouts.getLive())
        assertTrue(db.workoutSessionDao().observeAll().first().isEmpty())
    }

}
