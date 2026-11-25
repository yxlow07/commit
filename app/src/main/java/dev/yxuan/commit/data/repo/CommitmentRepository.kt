package dev.yxuan.commit.data.repo

import dev.yxuan.commit.data.db.dao.CommitmentDao
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.UUID

class CommitmentRepository(
    private val dao: CommitmentDao,
    private val clock: Clock,
    private val listener: DataChangeListener,
) {
    fun observeActive(): Flow<List<CommitmentEntity>> = dao.observeActive()

    fun observeWidgetTabs(): Flow<List<CommitmentEntity>> = dao.observeWidgetTabs()

    fun observe(id: String): Flow<CommitmentEntity?> = dao.observeById(id)

    suspend fun getActive(): List<CommitmentEntity> = dao.getActive()

    suspend fun create(name: String, type: CommitmentType, color: Int): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            CommitmentEntity(
                id = id,
                updatedAt = clock.millis(), dirty = true,
                name = name.trim(),
                type = type,
                color = color,
                sortOrder = dao.maxSortOrder() + 1,
                // Collections are standalone trackers: never a widget tab.
                showInWidget = type != CommitmentType.COLLECTION,
            )
        )
        listener.onDataChanged()
        return id
    }

    suspend fun update(id: String, name: String, color: Int, showInWidget: Boolean) =
        edit(id) { it.copy(name = name.trim(), color = color, showInWidget = showInWidget) }

    suspend fun archive(id: String) = edit(id) { it.copy(archived = true) }

    fun observeArchived(): Flow<List<CommitmentEntity>> = dao.observeArchived()

    /** Brings an archived commitment back, at the end of the list. */
    suspend fun restore(id: String) {
        val last = dao.maxSortOrder()
        edit(id) { it.copy(archived = false, sortOrder = last + 1) }
    }

    /** Saves [ids] as the new order of the active commitments; any not listed keep their place after them. */
    suspend fun reorder(ids: List<String>) {
        val active = dao.getActive()
        val byId = active.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] } + active.filter { it.id !in ids }
        val now = clock.millis()
        val changed = ordered.mapIndexedNotNull { index, c ->
            if (c.sortOrder == index) null else c.copy(sortOrder = index, updatedAt = now, dirty = true)
        }
        if (changed.isNotEmpty()) {
            dao.upsertAll(changed)
            listener.onDataChanged()
        }
    }

    /** Moves a commitment up (delta = -1) or down (delta = +1) and renumbers the rest. */
    suspend fun move(id: String, delta: Int) {
        val list = dao.getActive().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        val now = clock.millis()
        val changed = list.mapIndexedNotNull { index, c ->
            if (c.sortOrder == index) null else c.copy(sortOrder = index, updatedAt = now, dirty = true)
        }
        if (changed.isNotEmpty()) {
            dao.upsertAll(changed)
            listener.onDataChanged()
        }
    }

    private suspend fun edit(id: String, change: (CommitmentEntity) -> CommitmentEntity) {
        val current = dao.getById(id) ?: return
        dao.upsert(change(current).copy(updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }
}
