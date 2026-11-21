package dev.yxuan.commit.data.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.yxuan.commit.data.db.entity.TodoEntity
import kotlinx.coroutines.flow.Flow

/*
 * Undone todos sort by due date with undated ones last (F3.2).
 * The title breaks ties so the order stays stable between redraws.
 */
@Dao
interface TodoDao {
    @Query(
        "SELECT * FROM todos WHERE commitment_id = :commitmentId AND deleted = 0 AND done_at IS NULL " +
            "ORDER BY due_at IS NULL, due_at, title COLLATE NOCASE"
    )
    fun observeOpenForCommitment(commitmentId: String): Flow<List<TodoEntity>>

    /** Done todos of a commitment, most recently completed first. */
    @Query(
        "SELECT * FROM todos WHERE commitment_id = :commitmentId AND deleted = 0 AND done_at IS NOT NULL " +
            "ORDER BY done_at DESC"
    )
    fun observeDoneForCommitment(commitmentId: String): Flow<List<TodoEntity>>

    /** Open todos of every commitment shown on the widget, in widget order. */
    @Query(
        "SELECT t.* FROM todos t JOIN commitments c ON c.id = t.commitment_id " +
            "WHERE t.deleted = 0 AND t.done_at IS NULL " +
            "AND c.deleted = 0 AND c.archived = 0 AND c.show_in_widget = 1 " +
            "ORDER BY t.due_at IS NULL, t.due_at, t.title COLLATE NOCASE"
    )
    fun observeOpenForWidget(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: String): TodoEntity?

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Query("UPDATE todos SET done_at = :doneAt, updated_at = :now, dirty = 1 WHERE id = :id")
    suspend fun setDone(id: String, doneAt: Long?, now: Long)

    @Query("UPDATE todos SET deleted = 1, updated_at = :now, dirty = 1 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE todos SET last_done_at = :lastDoneAt, updated_at = :now, dirty = 1 WHERE id = :id")
    suspend fun setLastDoneAt(id: String, lastDoneAt: Long?, now: Long)

    /** Not-completed todos (one-off undone, and every recurring todo) of active commitments, for recaps. */
    @Query(
        "SELECT t.* FROM todos t JOIN commitments c ON c.id = t.commitment_id " +
            "WHERE t.deleted = 0 AND t.done_at IS NULL AND c.deleted = 0 AND c.archived = 0"
    )
    suspend fun getOpenInActiveCommitments(): List<TodoEntity>

    /** Every todo including deleted ones, so the alarm scheduler can also cancel stale alarms. */
    @Query("SELECT * FROM todos")
    suspend fun getAll(): List<TodoEntity>
}
