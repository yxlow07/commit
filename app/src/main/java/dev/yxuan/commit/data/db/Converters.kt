package dev.yxuan.commit.data.db

import androidx.room3.ColumnTypeConverter
import dev.yxuan.commit.data.db.entity.CollectionSort
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.db.entity.WorkoutPhase

/** Enums are stored as lower-case strings to match the Postgres schema (`standard` / `workout`). */
class Converters {
    @ColumnTypeConverter
    fun commitmentTypeToString(type: CommitmentType): String = type.name.lowercase()

    @ColumnTypeConverter
    fun stringToCommitmentType(value: String): CommitmentType =
        CommitmentType.valueOf(value.uppercase())

    @ColumnTypeConverter
    fun collectionSortToString(sort: CollectionSort): String = sort.name.lowercase()

    /** Unknown values (a newer app's sort) fall back to manual instead of failing the read. */
    @ColumnTypeConverter
    fun stringToCollectionSort(value: String): CollectionSort =
        CollectionSort.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CollectionSort.MANUAL

    @ColumnTypeConverter
    fun workoutPhaseToString(phase: WorkoutPhase): String = phase.name.lowercase()

    @ColumnTypeConverter
    fun stringToWorkoutPhase(value: String): WorkoutPhase = WorkoutPhase.valueOf(value.uppercase())
}
