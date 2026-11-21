package dev.yxuan.commit.data.repo

import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.ExerciseSessionPoint
import dev.yxuan.commit.data.db.dao.SessionSummary
import dev.yxuan.commit.data.db.dao.SetRow
import kotlinx.coroutines.flow.Flow
import java.time.Clock

/** History views (F8): completions, finished sessions, their sets, and exercise progress. */
class HistoryRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val listener: DataChangeListener,
) {
    fun observeCompletionsSince(from: Long): Flow<List<CompletionRow>> = db.completionDao().observeRowsSince(from)

    fun observeSessions(): Flow<List<SessionSummary>> = db.workoutSessionDao().observeFinishedSummaries()

    fun observeSession(id: String): Flow<SessionSummary?> = db.workoutSessionDao().observeSummary(id)

    fun observeSessionSets(sessionId: String): Flow<List<SetRow>> = db.workoutSetDao().observeRowsForSession(sessionId)

    fun observeExercisePoints(): Flow<List<ExerciseSessionPoint>> = db.workoutSetDao().observeExercisePoints()

    /** Fix a wrongly logged set (F8.5). */
    suspend fun editSet(id: String, reps: Int, weight: Double) {
        val set = db.workoutSetDao().getById(id) ?: return
        db.workoutSetDao().upsert(
            set.copy(reps = reps.coerceAtLeast(0), weight = weight.coerceAtLeast(0.0), updatedAt = clock.millis(), dirty = true)
        )
        listener.onDataChanged()
    }

    suspend fun deleteSet(id: String) {
        val set = db.workoutSetDao().getById(id) ?: return
        db.workoutSetDao().upsert(set.copy(deleted = true, updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }
}
