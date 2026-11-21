package dev.yxuan.commit.data.repo

import dev.yxuan.commit.data.db.withTransaction
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.CompletionEntity
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.domain.Recurrence
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * A todo's due date with an optional time. Stored as `due_at` epoch ms plus `has_time`;
 * a date-only due is stored as the start of that day in the device's zone.
 */
data class Due(val date: LocalDate, val time: LocalTime? = null) {
    fun toEpochMillis(clock: Clock): Long =
        (if (time != null) date.atTime(time) else date.atStartOfDay())
            .atZone(clock.zone).toInstant().toEpochMilli()

    companion object {
        fun of(todo: TodoEntity, clock: Clock): Due? {
            val dueAt = todo.dueAt ?: return null
            val local = java.time.Instant.ofEpochMilli(dueAt).atZone(clock.zone).toLocalDateTime()
            return Due(local.toLocalDate(), if (todo.hasTime) local.toLocalTime() else null)
        }
    }
}

class TodoRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val listener: DataChangeListener,
) {
    private val dao = db.todoDao()
    private val completions = db.completionDao()

    /**
     * Not-completed one-off todos plus all recurring todos of a commitment. Recurring "done" is
     * calculated from the clock, so callers map these through `TodoItem` (see `domain/Recurrence.kt`).
     */
    fun observeOpen(commitmentId: String): Flow<List<TodoEntity>> = dao.observeOpenForCommitment(commitmentId)

    /** Completed one-off todos. */
    fun observeDone(commitmentId: String): Flow<List<TodoEntity>> = dao.observeDoneForCommitment(commitmentId)

    fun observeOpenForWidget(): Flow<List<TodoEntity>> = dao.observeOpenForWidget()

    suspend fun get(id: String): TodoEntity? = dao.getById(id)

    suspend fun add(
        commitmentId: String,
        title: String,
        due: Due?,
        recurIntervalDays: Int? = null,
        recurDays: Int? = null,
        reminderLeadMinutes: Int? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            TodoEntity(
                id = id,
                updatedAt = clock.millis(), dirty = true,
                commitmentId = commitmentId,
                title = title.trim(),
                dueAt = due?.toEpochMillis(clock),
                hasTime = due?.time != null,
                recurIntervalDays = recurIntervalDays?.takeIf { it > 0 },
                recurDays = recurDays?.takeIf { it > 0 },
                reminderLeadMinutes = reminderLeadMinutes?.takeIf { it >= 0 },
            )
        )
        listener.onDataChanged()
        return id
    }

    /** Edits title, due date, recurrence, reminder lead time and commitment (moving it when the commitment changes). */
    suspend fun update(
        id: String,
        title: String,
        commitmentId: String,
        due: Due?,
        recurIntervalDays: Int? = null,
        recurDays: Int? = null,
        reminderLeadMinutes: Int? = null,
    ) {
        val current = dao.getById(id) ?: return
        dao.upsert(
            current.copy(
                title = title.trim(),
                commitmentId = commitmentId,
                dueAt = due?.toEpochMillis(clock),
                hasTime = due?.time != null,
                recurIntervalDays = recurIntervalDays?.takeIf { it > 0 },
                recurDays = recurDays?.takeIf { it > 0 },
                reminderLeadMinutes = reminderLeadMinutes?.takeIf { it >= 0 },
                updatedAt = clock.millis(), dirty = true,
            )
        )
        listener.onDataChanged()
    }

    /**
     * Marks done / undone (F2.2, F4.5). Every completion is logged in `completions`.
     * Recurring todos never set `done_at`: ticking sets `last_done_at`; unticking removes the
     * latest completion and recalculates `last_done_at` from the ones left.
     */
    suspend fun setDone(id: String, done: Boolean) {
        val changed = db.withTransaction {
            val todo = dao.getById(id) ?: return@withTransaction false
            if (Recurrence.isDone(todo, clock) == done) return@withTransaction false
            val now = clock.millis()
            if (done) {
                completions.upsert(CompletionEntity(id = UUID.randomUUID().toString(), updatedAt = now, dirty = true, todoId = id, doneAt = now))
                if (Recurrence.isRecurring(todo)) dao.setLastDoneAt(id, now, now) else dao.setDone(id, now, now)
            } else {
                completions.latestForTodo(id)?.let { completions.softDelete(it.id, now) }
                if (Recurrence.isRecurring(todo)) dao.setLastDoneAt(id, completions.maxDoneAt(id), now)
                else dao.setDone(id, null, now)
            }
            true
        }
        if (changed) listener.onDataChanged()
    }

    /** Soft delete (F2.3) so the deletion can sync. */
    suspend fun delete(id: String) {
        dao.softDelete(id, clock.millis())
        listener.onDataChanged()
    }
}
