package dev.yxuan.commit.data.repo

import dev.yxuan.commit.data.db.withTransaction
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

/** Exercise fields as edited in the routine editor (F5.1). */
data class ExerciseInput(
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val restSeconds: Int,
    /** Set for timed exercises (e.g. plank). */
    val durationSeconds: Int?,
)

/** Workout routines and their exercises (F5.1). Same write rules as the other repositories. */
class RoutineRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val listener: DataChangeListener,
) {
    private val routines = db.routineDao()
    private val exercises = db.exerciseDao()

    fun observeSummaries(): Flow<List<RoutineSummary>> = routines.observeSummaries()

    fun observeForCommitment(commitmentId: String): Flow<List<RoutineSummary>> =
        routines.observeSummaries().map { list -> list.filter { it.routine.commitmentId == commitmentId } }

    fun observe(id: String): Flow<RoutineEntity?> = routines.observeById(id)

    fun observeExercises(routineId: String): Flow<List<ExerciseEntity>> = exercises.observeForRoutine(routineId)

    suspend fun createRoutine(commitmentId: String, name: String): String {
        val id = UUID.randomUUID().toString()
        routines.upsert(
            RoutineEntity(
                id = id, updatedAt = clock.millis(), dirty = true, commitmentId = commitmentId,
                name = name.trim(), sortOrder = routines.maxSortOrder(commitmentId) + 1,
            )
        )
        listener.onDataChanged()
        return id
    }

    suspend fun updateRoutine(id: String, name: String, restBetweenExercisesS: Int, plannedDays: Int? = null) {
        val current = routines.getById(id) ?: return
        routines.upsert(
            current.copy(
                name = name.trim(), restBetweenExercisesS = restBetweenExercisesS.coerceAtLeast(0),
                plannedDays = plannedDays ?: current.plannedDays,
                updatedAt = clock.millis(), dirty = true,
            )
        )
        listener.onDataChanged()
    }

    suspend fun deleteRoutine(id: String) {
        val current = routines.getById(id) ?: return
        routines.upsert(current.copy(deleted = true, updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }

    /** Saves [ids] as the order of [commitmentId]'s routines; any not listed follow them. */
    suspend fun reorderRoutines(commitmentId: String, ids: List<String>) {
        val all = routines.getForCommitment(commitmentId)
        val byId = all.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] } + all.filter { it.id !in ids }
        val now = clock.millis()
        val changed = ordered.mapIndexedNotNull { i, r -> if (r.sortOrder == i) null else r.copy(sortOrder = i, updatedAt = now, dirty = true) }
        if (changed.isNotEmpty()) {
            routines.upsertAll(changed)
            listener.onDataChanged()
        }
    }

    suspend fun moveRoutine(id: String, delta: Int) {
        val current = routines.getById(id) ?: return
        val reordered = reorder(routines.getForCommitment(current.commitmentId), id, delta, { it.id }) { r, i ->
            if (r.sortOrder == i) null else r.copy(sortOrder = i, updatedAt = clock.millis(), dirty = true)
        } ?: return
        routines.upsertAll(reordered)
        listener.onDataChanged()
    }

    /** Adds ([id] = null) or edits an exercise. */
    suspend fun saveExercise(routineId: String, id: String?, input: ExerciseInput) {
        val now = clock.millis()
        val existing = id?.let { exercises.getById(it) }
        val base = existing ?: ExerciseEntity(
            id = UUID.randomUUID().toString(), updatedAt = now, dirty = true, routineId = routineId, name = "",
            sets = 1, reps = 0, restSeconds = 0, sortOrder = exercises.maxSortOrder(routineId) + 1,
        )
        exercises.upsert(
            base.copy(
                name = input.name.trim(),
                sets = input.sets.coerceAtLeast(1),
                reps = input.reps.coerceAtLeast(0),
                weight = input.weight.coerceAtLeast(0.0),
                restSeconds = input.restSeconds.coerceAtLeast(0),
                durationSeconds = input.durationSeconds?.takeIf { it > 0 },
                updatedAt = now, dirty = true,
            )
        )
        listener.onDataChanged()
    }

    suspend fun deleteExercise(id: String) {
        val current = exercises.getById(id) ?: return
        exercises.upsert(current.copy(deleted = true, updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }

    suspend fun moveExercise(id: String, delta: Int) {
        val current = exercises.getById(id) ?: return
        db.withTransaction {
            val reordered = reorder(exercises.getForRoutine(current.routineId), id, delta, { it.id }) { e, i ->
                if (e.sortOrder == i) null else e.copy(sortOrder = i, updatedAt = clock.millis(), dirty = true)
            } ?: return@withTransaction
            exercises.upsertAll(reordered)
        }
        listener.onDataChanged()
    }

    /** Moves one item by [delta] and returns the rows whose sort order changed, or null for a no-op. */
    private fun <T> reorder(
        items: List<T>, id: String, delta: Int, idOf: (T) -> String, renumber: (T, Int) -> T?,
    ): List<T>? {
        val list = items.toMutableList()
        val from = list.indexOfFirst { idOf(it) == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return null
        list.add(to, list.removeAt(from))
        return list.mapIndexedNotNull { i, item -> renumber(item, i) }.ifEmpty { null }
    }
}
