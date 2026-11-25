package dev.yxuan.commit.data.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommitmentDao {
    /** Commitments shown in the app: not deleted, not archived, in user order. */
    @Query("SELECT * FROM commitments WHERE deleted = 0 AND archived = 0 ORDER BY sort_order, name")
    fun observeActive(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE deleted = 0 AND archived = 0 ORDER BY sort_order, name")
    suspend fun getActive(): List<CommitmentEntity>

    /** Archived (not deleted) commitments, most recently changed first. */
    @Query("SELECT * FROM commitments WHERE deleted = 0 AND archived = 1 ORDER BY updated_at DESC")
    fun observeArchived(): Flow<List<CommitmentEntity>>

    /** Commitments that appear as widget tabs (F3.1, F1.3). */
    @Query(
        "SELECT * FROM commitments WHERE deleted = 0 AND archived = 0 AND show_in_widget = 1 AND type != 'collection' " +
            "ORDER BY sort_order, name"
    )
    fun observeWidgetTabs(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE id = :id")
    fun observeById(id: String): Flow<CommitmentEntity?>

    @Query("SELECT * FROM commitments WHERE id = :id")
    suspend fun getById(id: String): CommitmentEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM commitments WHERE deleted = 0")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsert(commitment: CommitmentEntity)

    /** Upserts in one transaction (Room wraps multi-row writes). */
    @Upsert
    suspend fun upsertAll(commitments: List<CommitmentEntity>)
}
