package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import dev.yxuan.commit.data.db.entity.CollectionSort

/** Group sort orders: the label shown in the sort menu and the order each one gives. */
object CollectionSorting {
    fun label(sort: CollectionSort): String = when (sort) {
        CollectionSort.MANUAL -> "Manual"
        CollectionSort.NAME -> "Name A–Z"
        CollectionSort.MISSING_FIRST -> "Missing first"
        CollectionSort.COLLECTED_FIRST -> "Collected first"
        CollectionSort.NEWEST -> "Newest"
    }

    private val byName = compareBy<CollectionItemEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }
    private val manual = compareBy<CollectionItemEntity>({ it.sortOrder }, { it.createdAt })

    /** Items in [sort] order; ties fall back to manual order so the list never jumps around. */
    fun sorted(items: List<CollectionItemEntity>, sort: CollectionSort): List<CollectionItemEntity> = items.sortedWith(
        when (sort) {
            CollectionSort.MANUAL -> manual
            // Unnamed (photo-only) items go last.
            CollectionSort.NAME -> compareBy<CollectionItemEntity> { it.name.isNullOrBlank() }.then(byName).then(manual)
            CollectionSort.MISSING_FIRST -> compareBy<CollectionItemEntity> { it.collected }.then(manual)
            CollectionSort.COLLECTED_FIRST -> compareBy<CollectionItemEntity> { !it.collected }.then(manual)
            CollectionSort.NEWEST -> compareByDescending<CollectionItemEntity> { it.createdAt }.then(manual)
        },
    )
}
