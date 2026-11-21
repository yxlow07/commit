package dev.yxuan.commit.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.db.entity.WorkoutSessionEntity
import dev.yxuan.commit.data.db.entity.WorkoutSetEntity
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.DataChangeListener
import dev.yxuan.commit.data.repo.Due
import dev.yxuan.commit.data.repo.ExerciseInput
import dev.yxuan.commit.data.repo.HistoryRepository
import dev.yxuan.commit.data.repo.RecapRepository
import dev.yxuan.commit.data.repo.RoutineRepository
import dev.yxuan.commit.data.repo.SettingsRepository
import dev.yxuan.commit.data.repo.TodoRepository
import dev.yxuan.commit.domain.RecapKind
import dev.yxuan.commit.domain.Weekdays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RecapHistoryTest {
    private lateinit var db: AppDatabase
    // Saturday 21 Nov 2026, 21:00 UTC.
    private val clock = Clock.fixed(Instant.parse("2026-11-21T21:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 21)
    private val noop = DataChangeListener { }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).setDriver(BundledSQLiteDriver()).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun recapsAreBuiltFromTheDatabase() = runTest {
        val commitments = CommitmentRepository(db.commitmentDao(), clock, noop)
        val todos = TodoRepository(db, clock, noop)
        val routines = RoutineRepository(db, clock, noop)
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val gym = commitments.create("Gym", CommitmentType.WORKOUT, 0)
        todos.add(study, "Essay", Due(today.minusDays(1)))
        val quiz = todos.add(study, "Quiz", Due(today))
        todos.add(study, "Slides", Due(today.plusDays(1)))
        val push = routines.createRoutine(gym, "Push Day")
        routines.updateRoutine(push, "Push Day", 120, Weekdays.bit(today.dayOfWeek))
        todos.setDone(quiz, true)

        val recaps = RecapRepository(db, clock)
        val morning = recaps.build(RecapKind.MORNING).sections.associate { it.title to it.lines }
        assertEquals(listOf("Essay · Study · Overdue · 1d"), morning["Overdue"])
        assertEquals(listOf("Today: Push Day"), morning["Workout"])

        val night = recaps.build(RecapKind.NIGHT).sections.associate { it.title to it.lines }
        assertEquals(listOf("Study · Quiz"), night["Done today"])
        assertEquals(listOf("Slides · Study · Tomorrow"), night["Due tomorrow"])
    }

    @Test
    fun settingsDefaultsUntilFirstChange() = runTest {
        val settings = SettingsRepository(db, clock, noop)
        assertEquals(7 * 60 + 30, settings.get().morningRecapTime)
        settings.update { it.copy(nightRecapTime = 21 * 60, nightRecapEnabled = false) }
        val saved = settings.observe().first()
        assertEquals(21 * 60, saved.nightRecapTime)
        assertEquals(false, saved.nightRecapEnabled)
        assertEquals(SettingsRepository.LOCAL_ID, saved.id)
    }

    @Test
    fun editingAPastSetUpdatesSessionVolumeAndProgress() = runTest {
        val commitments = CommitmentRepository(db.commitmentDao(), clock, noop)
        val routines = RoutineRepository(db, clock, noop)
        val history = HistoryRepository(db, clock, noop)
        val gym = commitments.create("Gym", CommitmentType.WORKOUT, 0)
        val push = routines.createRoutine(gym, "Push Day")
        routines.saveExercise(push, null, ExerciseInput("Bench", 2, 8, 60.0, 90, null))
        val bench = db.exerciseDao().getForRoutine(push).single()
        val start = clock.millis() - 3_600_000
        db.workoutSessionDao().upsert(WorkoutSessionEntity(id = "s1", updatedAt = start, routineId = push, startedAt = start, endedAt = start + 1_800_000))
        db.workoutSetDao().upsertAll(
            listOf(1, 2).map { n ->
                WorkoutSetEntity(id = "w$n", updatedAt = start, sessionId = "s1", exerciseId = bench.id, setNo = n, reps = 8, weight = 60.0, doneAt = start + n * 60_000)
            }
        )
        assertEquals(960.0, history.observeSessions().first().single().volume, 0.0)

        history.editSet("w2", reps = 6, weight = 70.0) // fix a wrongly logged set (F8.5)
        val summary = history.observeSessions().first().single()
        assertEquals(8 * 60.0 + 6 * 70.0, summary.volume, 0.0)
        assertEquals(70.0, history.observeExercisePoints().first().single().bestWeight, 0.0)

        history.deleteSet("w1")
        assertEquals(1, history.observeSessions().first().single().setCount)
    }

}
