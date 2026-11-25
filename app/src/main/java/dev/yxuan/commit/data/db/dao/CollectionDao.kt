package dev.yxuan.commit.data.db.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.yxuan.commit.data.db.entity.CollectionGroupEntity
import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import kotlinx.coroutines.flow.Flow

/** "34 of 50" for a collection commitment, for the Groups tab. */
data class CollectionProgress(val commitmentId: String, val collected: Int, val total: Int)

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collection_groups WHERE commitment_id = :commitmentId AND deleted = 0 ORDER BY sort_order, name")
    fun observeGroups(commitmentId: String): Flow<List<CollectionGroupEntity>>

    @Query("SELECT * FROM collection_groups WHERE commitment_id = :commitmentId AND deleted = 0 ORDER BY sort_order, name")
    suspend fun getGroups(commitmentId: String): List<CollectionGroupEntity>

    @Query("SELECT * FROM collection_groups WHERE id = :id")
    suspend fun getGroup(id: String): CollectionGroupEntity?

    @Query(
        "SELECT i.* FROM collection_items i JOIN collection_groups g ON g.id = i.group_id " +
            "WHERE g.commitment_id = :commitmentId AND i.deleted = 0 AND g.deleted = 0 ORDER BY i.sort_order, i.created_at"
    )
    fun observeItems(commitmentId: String): Flow<List<CollectionItemEntity>>

    @Query(
        "SELECT i.* FROM collection_items i JOIN collection_groups g ON g.id = i.group_id " +
            "WHERE g.commitment_id = :commitmentId AND i.deleted = 0 AND g.deleted = 0"
    )
    suspend fun getItems(commitmentId: String): List<CollectionItemEntity>

    @Query("SELECT * FROM collection_items WHERE id = :id")
    suspend fun getItem(id: String): CollectionItemEntity?

    @Query("SELECT * FROM collection_items WHERE group_id = :groupId AND deleted = 0")
    suspend fun getItemsInGroup(groupId: String): List<CollectionItemEntity>

    @Query(
        "SELECT g.commitment_id AS commitmentId, SUM(i.collected) AS collected, COUNT(i.id) AS total " +
            "FROM collection_groups g JOIN collection_items i ON i.group_id = g.id " +
            "WHERE g.deleted = 0 AND i.deleted = 0 GROUP BY g.commitment_id"
    )
    fun observeProgress(): Flow<List<CollectionProgress>>

    /** Items whose photo is on this phone and not uploaded yet (photo sync). */
    @Query("SELECT * FROM collection_items WHERE photo_dirty = 1 AND deleted = 0")
    suspend fun getPhotosToUpload(): List<CollectionItemEntity>

    /** Items with an uploaded photo, for downloading the ones missing on this phone. */
    @Query("SELECT * FROM collection_items WHERE photo_path IS NOT NULL AND deleted = 0")
    suspend fun getUploadedPhotos(): List<CollectionItemEntity>

    @Query("UPDATE collection_items SET photo_path = :path, photo_dirty = 0, updated_at = :now, dirty = 1 WHERE id = :id")
    suspend fun setUploaded(id: String, path: String, now: Long)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM collection_groups WHERE commitment_id = :commitmentId AND deleted = 0")
    suspend fun maxGroupOrder(commitmentId: String): Int

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM collection_items WHERE group_id = :groupId AND deleted = 0")
    suspend fun maxItemOrder(groupId: String): Int

    @Upsert
    suspend fun upsertGroup(group: CollectionGroupEntity)

    @Upsert
    suspend fun upsertGroups(groups: List<CollectionGroupEntity>)

    @Upsert
    suspend fun upsertItem(item: CollectionItemEntity)

    @Upsert
    suspend fun upsertItems(items: List<CollectionItemEntity>)

    @Query("UPDATE collection_items SET deleted = 1, updated_at = :now, dirty = 1 WHERE group_id = :groupId AND deleted = 0")
    suspend fun softDeleteItemsInGroup(groupId: String, now: Long)
}
