package dev.yxuan.commit.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** How a collection group orders its items; stored lowercase (`manual`, `name`, …). */
enum class CollectionSort { MANUAL, NAME, MISSING_FIRST, COLLECTED_FIRST, NEWEST }

/**
 * A group inside a collection commitment (e.g. "1990s" in "Stamps"). Synced like every other
 * table (same sync columns as [CommitmentEntity]).
 */
@Entity(
    tableName = "collection_groups",
    indices = [Index(value = ["user_id", "updated_at"]), Index(value = ["commitment_id"])],
)
data class CollectionGroupEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "commitment_id") val commitmentId: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "sort_mode", defaultValue = "manual") val sortMode: CollectionSort = CollectionSort.MANUAL,
)

/**
 * An item in a group: a name, a photo, or both, and whether it's collected. [photoPath] is the
 * Storage key (`{user_id}/{id}-{time}.jpg` or `.png`), set once the photo is uploaded; the phone keeps its copy at
 * `filesDir/collection-photos/{id}.jpg`. [photoDirty] (phone-only, never synced) means that file is
 * new or changed and still needs uploading.
 */
@Entity(
    tableName = "collection_items",
    indices = [Index(value = ["user_id", "updated_at"]), Index(value = ["group_id"])],
)
data class CollectionItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "group_id") val groupId: String,
    val name: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    val collected: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "photo_dirty", defaultValue = "0") val photoDirty: Boolean = false,
) {
    val hasPhoto: Boolean get() = photoPath != null || photoDirty
}
