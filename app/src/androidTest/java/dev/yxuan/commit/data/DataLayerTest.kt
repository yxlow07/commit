package dev.yxuan.commit.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.DataChangeListener
import dev.yxuan.commit.data.repo.Due
import dev.yxuan.commit.data.repo.TodoRepository
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DataLayerTest {
    private lateinit var db: AppDatabase
    private lateinit var commitments: CommitmentRepository
    private lateinit var todos: TodoRepository
    private var changes = 0

    private val zone: ZoneId = ZoneOffset.UTC
    private val clock = Clock.fixed(Instant.parse("2026-11-21T12:00:00Z"), zone)
    private val today = LocalDate.of(2026, 11, 21)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        val listener = DataChangeListener { changes++ }
        commitments = CommitmentRepository(db.commitmentDao(), clock, listener)
        todos = TodoRepository(db, clock, listener)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun openTodosSortByDueDateWithUndatedLast() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val undated = todos.add(study, "Read chapter", null)
        val later = todos.add(study, "Essay", Due(today.plusDays(3)))
        val soon = todos.add(study, "Quiz", Due(today, LocalTime.of(15, 0)))
        val overdue = todos.add(study, "Problem set", Due(today.minusDays(1)))

        val open = todos.observeOpen(study).first().map { it.id }
        assertEquals(listOf(overdue, soon, later, undated), open)
    }

    @Test
    fun openTodosExcludeDoneDeletedAndOtherCommitments() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val work = commitments.create("Work", CommitmentType.STANDARD, 0)
        val keep = todos.add(study, "Keep", null)
        val done = todos.add(study, "Done", null)
        val deleted = todos.add(study, "Deleted", null)
        todos.add(work, "Other commitment", null)

        todos.setDone(done, true)
        todos.delete(deleted)

        assertEquals(listOf(keep), todos.observeOpen(study).first().map { it.id })
        assertEquals(listOf(done), todos.observeDone(study).first().map { it.id })
        // Soft delete: the row is still there, flagged.
        assertTrue(todos.get(deleted)!!.deleted)
    }

    @Test
    fun untickRestoresTodo() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val id = todos.add(study, "Quiz", null)
        todos.setDone(id, true)
        assertNotNull(todos.get(id)!!.doneAt)
        todos.setDone(id, false)
        assertNull(todos.get(id)!!.doneAt)
    }

    @Test
    fun writesMarkRowsDirtyWithTimestampAndNotifyListener() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val id = todos.add(study, "  Quiz  ", Due(today, LocalTime.of(9, 0)))
        val todo = todos.get(id)!!

        assertEquals("Quiz", todo.title)
        assertEquals(clock.millis(), todo.updatedAt)
        assertTrue(todo.hasTime)
        assertEquals(Due(today, LocalTime.of(9, 0)), Due.of(todo, clock))
        assertEquals(2, changes)
    }

    @Test
    fun widgetQueryOnlyIncludesVisibleCommitments() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val hidden = commitments.create("Hidden", CommitmentType.STANDARD, 0)
        val archived = commitments.create("Archived", CommitmentType.STANDARD, 0)
        commitments.update(hidden, "Hidden", 0, showInWidget = false)
        commitments.archive(archived)
        val visible = todos.add(study, "Visible", null)
        todos.add(hidden, "Not on widget", null)
        todos.add(archived, "Archived todo", null)

        assertEquals(listOf(study), commitments.observeWidgetTabs().first().map { it.id })
        assertEquals(listOf(visible), todos.observeOpenForWidget().first().map { it.id })
    }

    @Test
    fun moveReordersCommitments() = runTest {
        val a = commitments.create("A", CommitmentType.STANDARD, 0)
        val b = commitments.create("B", CommitmentType.STANDARD, 0)
        val c = commitments.create("C", CommitmentType.WORKOUT, 0)

        commitments.move(c, -1)
        assertEquals(listOf(a, c, b), commitments.observeActive().first().map { it.id })

        commitments.move(a, -1) // already first: no-op
        assertEquals(listOf(a, c, b), commitments.observeActive().first().map { it.id })

        commitments.archive(c)
        assertEquals(listOf(a, b), commitments.observeActive().first().map { it.id })
    }

    @Test
    fun recurringTickLogsCompletionAndUntickRecalculatesLastDone() = runTest {
        val home = commitments.create("Home", CommitmentType.STANDARD, 0)
        val laundry = todos.add(home, "Laundry", null, recurIntervalDays = 4)

        // Done 5 days ago with a 4-day interval, so it is due again now.
        val earlier = clock.millis() - 5 * 86_400_000L
        db.completionDao().upsert(
            dev.yxuan.commit.data.db.entity.CompletionEntity(id = "old", updatedAt = earlier, todoId = laundry, doneAt = earlier)
        )
        db.todoDao().setLastDoneAt(laundry, earlier, earlier)

        todos.setDone(laundry, true)
        var todo = todos.get(laundry)!!
        assertEquals(clock.millis(), todo.lastDoneAt)
        assertNull(todo.doneAt) // recurring todos never set done_at
        assertEquals(2, db.completionDao().observeForTodo(laundry).first().size)

        todos.setDone(laundry, true) // already done: no second completion
        assertEquals(2, db.completionDao().observeForTodo(laundry).first().size)

        todos.setDone(laundry, false)
        todo = todos.get(laundry)!!
        assertEquals(earlier, todo.lastDoneAt)
        assertEquals(listOf("old"), db.completionDao().observeForTodo(laundry).first().map { it.id })
    }

    @Test
    fun oneOffCompletionIsLoggedAndRemovedOnUntick() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val id = todos.add(study, "Quiz", null)
        todos.setDone(id, true)
        assertEquals(1, db.completionDao().observeForTodo(id).first().size)
        todos.setDone(id, false)
        assertTrue(db.completionDao().observeForTodo(id).first().isEmpty())
    }
}
