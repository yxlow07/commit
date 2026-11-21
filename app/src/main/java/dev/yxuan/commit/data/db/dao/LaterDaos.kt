package dev.yxuan.commit.data.db.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Upsert
import dev.yxuan.commit.data.db.entity.CompletionEntity
import dev.yxuan.commit.data.db.entity.SettingsEntity
import dev.yxuan.commit.data.db.entity.ThemeEntity
import kotlinx.coroutines.flow.Flow

// Smaller DAOs: completions (M3), themes (M9), settings (M5). Workout DAOs are in WorkoutDaos.kt.

/** A completion with its todo and commitment, for recaps and history. */
data class CompletionRow(
    val completionId: String,
    val todoId: String,
    val title: String,
    val commitmentId: String,
    val commitmentName: String,
    val commitmentColor: Int,
    val doneAt: Long,
    val dueAt: Long?,
    val hasTime: Boolean,
    val recurIntervalDays: Int?,
)

private const val COMPLETION_ROW_SELECT =
    "SELECT c.id AS completionId, t.id AS todoId, t.title AS title, t.commitment_id AS commitmentId, " +
        "m.name AS commitmentName, m.color AS commitmentColor, c.done_at AS doneAt, t.due_at AS dueAt, " +
        "t.has_time AS hasTime, t.recur_interval_days AS recurIntervalDays " +
        "FROM completions c JOIN todos t ON t.id = c.todo_id JOIN commitments m ON m.id = t.commitment_id " +
        "WHERE c.deleted = 0 AND t.deleted = 0 AND m.deleted = 0 "

@Dao
interface CompletionDao {
    /** Completions in [from, to), newest first. */
    @Query(COMPLETION_ROW_SELECT + "AND c.done_at >= :from AND c.done_at < :to ORDER BY c.done_at DESC")
    suspend fun rowsBetween(from: Long, to: Long): List<CompletionRow>

    @Query(COMPLETION_ROW_SELECT + "AND c.done_at >= :from ORDER BY c.done_at DESC")
    fun observeRowsSince(from: Long): Flow<List<CompletionRow>>

    @Upsert
    suspend fun upsert(completion: CompletionEntity)

    @Query("SELECT * FROM completions WHERE todo_id = :todoId AND deleted = 0 ORDER BY done_at DESC")
    fun observeForTodo(todoId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE todo_id = :todoId AND deleted = 0 ORDER BY done_at DESC LIMIT 1")
    suspend fun latestForTodo(todoId: String): CompletionEntity?

    @Query("SELECT MAX(done_at) FROM completions WHERE todo_id = :todoId AND deleted = 0")
    suspend fun maxDoneAt(todoId: String): Long?

    @Query("UPDATE completions SET deleted = 1, updated_at = :now, dirty = 1 WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}

@Dao
interface ThemeDao {
    @Upsert
    suspend fun upsert(theme: ThemeEntity)

    @Query("SELECT * FROM themes WHERE deleted = 0 ORDER BY name")
    fun observeAll(): Flow<List<ThemeEntity>>
}

@Dao
interface SettingsDao {
    @Upsert
    suspend fun upsert(settings: SettingsEntity)

    @Query("SELECT * FROM settings WHERE deleted = 0 LIMIT 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE deleted = 0 LIMIT 1")
    suspend fun get(): SettingsEntity?

    @Query("SELECT * FROM settings WHERE id = :id")
    suspend fun getById(id: String): SettingsEntity?

    @Query("DELETE FROM settings WHERE id = :id")
    suspend fun deleteById(id: String)
}
