package dev.yxuan.commit.data.repo

import android.net.Uri
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.dao.CollectionProgress
import dev.yxuan.commit.data.db.entity.CollectionGroupEntity
import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import dev.yxuan.commit.data.db.entity.CollectionSort
import dev.yxuan.commit.data.db.withTransaction
import dev.yxuan.commit.data.photo.CollectionPhotos
import dev.yxuan.commit.domain.ImportPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID

/** What an item editor saves: [photo] is a newly picked or captured image, or null to keep the current one. */
data class ItemInput(val name: String?, val groupId: String, val collected: Boolean, val photo: Uri? = null, val removePhoto: Boolean = false)

/** "Imported 48 items in 6 groups, 2 files skipped". */
data class ImportResult(val added: Int, val updated: Int, val groups: Int, val skipped: Int) {
    val message: String
        get() = buildList {
            add("Imported ${added + updated} ${if (added + updated == 1) "item" else "items"} in $groups ${if (groups == 1) "group" else "groups"}")
            if (updated > 0) add("$updated updated")
            if (skipped > 0) add("$skipped ${if (skipped == 1) "file" else "files"} skipped")
        }.joinToString(", ")
}

/**
 * Collections (a commitment of type COLLECTION): its groups and items, photos and zip import. Every
 * write marks rows dirty and calls [listener], so sync uploads it (photos follow in photo sync).
 */
class CollectionRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val photos: CollectionPhotos,
    private val listener: DataChangeListener,
) {
    private val dao = db.collectionDao()

    fun observeGroups(commitmentId: String): Flow<List<CollectionGroupEntity>> = dao.observeGroups(commitmentId)
    fun observeItems(commitmentId: String): Flow<List<CollectionItemEntity>> = dao.observeItems(commitmentId)
    fun observeProgress(): Flow<List<CollectionProgress>> = dao.observeProgress()

    // ---- Groups ----

    suspend fun createGroup(commitmentId: String, name: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertGroup(CollectionGroupEntity(id = id, updatedAt = clock.millis(), commitmentId = commitmentId, name = name.trim(), sortOrder = dao.maxGroupOrder(commitmentId) + 1))
        listener.onDataChanged()
        return id
    }

    suspend fun renameGroup(id: String, name: String) = updateGroup(id) { it.copy(name = name.trim()) }
    suspend fun setSort(id: String, sort: CollectionSort) = updateGroup(id) { it.copy(sortMode = sort) }

    /** Deletes a group and its items (soft deletes, so they sync). */
    suspend fun deleteGroup(id: String) {
        val now = clock.millis()
        db.withTransaction {
            dao.getGroup(id)?.let { dao.upsertGroup(it.copy(deleted = true, updatedAt = now, dirty = true)) }
            dao.softDeleteItemsInGroup(id, now)
        }
        listener.onDataChanged()
    }

    /** Saves [ids] as the new group order (drag to reorder). */
    suspend fun reorderGroups(commitmentId: String, ids: List<String>) {
        val now = clock.millis()
        val changed = dao.getGroups(commitmentId).mapNotNull { g ->
            val i = ids.indexOf(g.id)
            if (i >= 0 && i != g.sortOrder) g.copy(sortOrder = i, updatedAt = now, dirty = true) else null
        }
        if (changed.isEmpty()) return
        dao.upsertGroups(changed)
        listener.onDataChanged()
    }

    /** Saves [ids] as the manual order of a group's items. */
    suspend fun reorderItems(groupId: String, ids: List<String>) {
        val now = clock.millis()
        val changed = dao.getItemsInGroup(groupId).mapNotNull { item ->
            val i = ids.indexOf(item.id)
            if (i >= 0 && i != item.sortOrder) item.copy(sortOrder = i, updatedAt = now, dirty = true) else null
        }
        if (changed.isEmpty()) return
        dao.upsertItems(changed)
        listener.onDataChanged()
    }

    private suspend fun updateGroup(id: String, change: (CollectionGroupEntity) -> CollectionGroupEntity) {
        val g = dao.getGroup(id) ?: return
        dao.upsertGroup(change(g).copy(updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }

    // ---- Items ----

    suspend fun toggle(itemId: String) {
        val item = dao.getItem(itemId) ?: return
        dao.upsertItem(item.copy(collected = !item.collected, updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }

    /** Creates ([itemId] null) or edits an item. A new photo is downscaled and queued for upload. */
    suspend fun saveItem(itemId: String?, input: ItemInput): String {
        val now = clock.millis()
        val id = itemId ?: UUID.randomUUID().toString()
        val existing = itemId?.let { dao.getItem(it) }
        input.photo?.let { uri -> withContext(Dispatchers.IO) { photos.saveFromUri(uri, id) } }
        val base = existing ?: CollectionItemEntity(id = id, updatedAt = now, groupId = input.groupId, sortOrder = dao.maxItemOrder(input.groupId) + 1, createdAt = now)
        // Moving to another group puts it at the end there.
        val order = if (existing != null && existing.groupId != input.groupId) dao.maxItemOrder(input.groupId) + 1 else base.sortOrder
        var item = base.copy(
            name = input.name?.trim()?.ifEmpty { null },
            groupId = input.groupId,
            collected = input.collected,
            sortOrder = order,
            updatedAt = now,
            dirty = true,
        )
        if (input.photo != null) item = item.copy(photoDirty = true)
        if (input.removePhoto && input.photo == null) {
            existing?.let(photos::deleteLocal)
            item = item.copy(photoPath = null, photoDirty = false)
        }
        dao.upsertItem(item)
        listener.onDataChanged()
        return id
    }

    suspend fun deleteItem(itemId: String) {
        val item = dao.getItem(itemId) ?: return
        dao.upsertItem(item.copy(deleted = true, updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }

    // ---- Import ----

    /**
     * Writes a parsed zip into [commitmentId]: groups are matched by name (new ones are added at the
     * end), items by group + name (updated, not duplicated); photos are saved and queued for upload.
     */
    suspend fun import(commitmentId: String, plan: ImportPlan): ImportResult {
        val now = clock.millis()
        val groups = dao.getGroups(commitmentId).associateBy { it.name.lowercase() }.toMutableMap()
        val existing = dao.getItems(commitmentId)
        var nextGroupOrder = (groups.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newGroups = mutableListOf<CollectionGroupEntity>()
        for (name in plan.groups) {
            if (name.lowercase() in groups) continue
            val g = CollectionGroupEntity(id = UUID.randomUUID().toString(), updatedAt = now, commitmentId = commitmentId, name = name, sortOrder = nextGroupOrder++)
            groups[name.lowercase()] = g
            newGroups += g
        }
        val nextItemOrder = HashMap<String, Int>()
        groups.values.forEach { g -> nextItemOrder[g.id] = (existing.filter { it.groupId == g.id }.maxOfOrNull { it.sortOrder } ?: -1) + 1 }
        val items = mutableListOf<CollectionItemEntity>()
        var added = 0
        var updated = 0
        plan.items.forEachIndexed { index, it ->
            val group = groups.getValue(it.group.lowercase())
            val match = it.name?.let { n -> existing.firstOrNull { e -> e.groupId == group.id && e.name.equals(n, ignoreCase = true) } }
            val id = match?.id ?: UUID.randomUUID().toString()
            val bytes = it.imagePath?.let(plan.images::get)
            val photoSaved = bytes != null && runCatching { withContext(Dispatchers.IO) { photos.saveFromBytes(bytes, id) } }.isSuccess
            val item = (match ?: CollectionItemEntity(
                id = id, updatedAt = now, groupId = group.id, sortOrder = nextItemOrder.merge(group.id, 1, Int::plus)!! - 1,
                // Keep the zip's order for "Newest" too: later rows count as slightly newer.
                createdAt = now + index,
            )).copy(name = it.name, collected = it.collected || (match?.collected ?: false), updatedAt = now, dirty = true)
                .let { i -> if (photoSaved) i.copy(photoDirty = true) else i }
            if (match == null) added++ else updated++
            items += item
        }
        db.withTransaction {
            if (newGroups.isNotEmpty()) dao.upsertGroups(newGroups)
            // A sync may have changed matched rows since they were read (e.g. a photo upload set
            // photo_path), so apply the import's changes onto the current row, not the old copy.
            val current = dao.getItems(commitmentId).associateBy { it.id }
            dao.upsertItems(items.map { item ->
                val now0 = current[item.id] ?: return@map item
                now0.copy(name = item.name, collected = item.collected, updatedAt = item.updatedAt, dirty = true, photoDirty = now0.photoDirty || item.photoDirty)
            })
        }
        listener.onDataChanged()
        return ImportResult(added, updated, plan.groups.size, plan.warnings.size)
    }
}
