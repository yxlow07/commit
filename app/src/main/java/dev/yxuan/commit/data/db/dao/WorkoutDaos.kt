package dev.yxuan.commit.data.db.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Upsert
import dev.yxuan.commit.data.db.entity.ActiveWorkoutEntity
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.db.entity.WorkoutSessionEntity
import dev.yxuan.commit.data.db.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

/** A routine with its number of (not deleted) exercises, for pickers. */
data class RoutineSummary(
    @Embedded val routine: RoutineEntity,
    val exerciseCount: Int,
)

/** A session with its routine name and set totals, for recaps and history (F8.4). */
data class SessionSummary(
    @Embedded val session: WorkoutSessionEntity,
    val routineName: String,
    val volume: Double,
    val setCount: Int,
)

private const val SESSION_SUMMARY_SELECT =
    "SELECT s.*, r.name AS routineName, " +
        "(SELECT COALESCE(SUM(w.reps * w.weight), 0) FROM workout_sets w WHERE w.session_id = s.id AND w.deleted = 0) AS volume, " +
        "(SELECT COUNT(*) FROM workout_sets w WHERE w.session_id = s.id AND w.deleted = 0) AS setCount " +
        "FROM workout_sessions s JOIN routines r ON r.id = s.routine_id WHERE s.deleted = 0 "

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE deleted = 0 ORDER BY sort_order, name")
    suspend fun getAll(): List<RoutineEntity>

    @Upsert
    suspend fun upsert(routine: RoutineEntity)

    @Upsert
    suspend fun upsertAll(routines: List<RoutineEntity>)

    @Query(
        "SELECT r.*, (SELECT COUNT(*) FROM exercises e WHERE e.routine_id = r.id AND e.deleted = 0) AS exerciseCount " +
            "FROM routines r WHERE r.deleted = 0 ORDER BY r.sort_order, r.name"
    )
    fun observeSummaries(): Flow<List<RoutineSummary>>

    @Query("SELECT * FROM routines WHERE commitment_id = :commitmentId AND deleted = 0 ORDER BY sort_order, name")
    suspend fun getForCommitment(commitmentId: String): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeById(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM routines WHERE commitment_id = :commitmentId AND deleted = 0")
    suspend fun maxSortOrder(commitmentId: String): Int
}

@Dao
interface ExerciseDao {
    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercises WHERE routine_id = :routineId AND deleted = 0 ORDER BY sort_order")
    fun observeForRoutine(routineId: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE routine_id = :routineId AND deleted = 0 ORDER BY sort_order")
    suspend fun getForRoutine(routineId: String): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM exercises WHERE routine_id = :routineId AND deleted = 0")
    suspend fun maxSortOrder(routineId: String): Int
}

@Dao
interface WorkoutSessionDao {
    @Query(SESSION_SUMMARY_SELECT + "AND s.id = :id")
    fun observeSummary(id: String): Flow<SessionSummary?>

    @Upsert
    suspend fun upsert(session: WorkoutSessionEntity)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE deleted = 0 ORDER BY started_at DESC")
    fun observeAll(): Flow<List<WorkoutSessionEntity>>

    /** Finished sessions that ended in [from, to). */
    @Query(SESSION_SUMMARY_SELECT + "AND s.ended_at >= :from AND s.ended_at < :to ORDER BY s.started_at")
    suspend fun summariesEndedBetween(from: Long, to: Long): List<SessionSummary>

    /** All finished sessions, newest first. */
    @Query(SESSION_SUMMARY_SELECT + "AND s.ended_at IS NOT NULL ORDER BY s.started_at DESC")
    fun observeFinishedSummaries(): Flow<List<SessionSummary>>

    /** Local retention: synced sessions that ended before [cutoff] and have no unsynced sets. Sets go first ([WorkoutSetDao.pruneForOldSessions]). */
    @Query(
        """DELETE FROM workout_sessions WHERE ended_at IS NOT NULL AND ended_at < :cutoff AND dirty = 0
           AND NOT EXISTS (SELECT 1 FROM workout_sets ws WHERE ws.session_id = workout_sessions.id AND ws.dirty = 1)"""
    )
    suspend fun pruneOld(cutoff: Long): Int
}

/** A logged set with its exercise name, for the session detail screen (F8.5). */
data class SetRow(
    @Embedded val set: WorkoutSetEntity,
    val exerciseName: String,
    val exerciseOrder: Int,
)

/** Best weight and volume of one exercise (matched by name) in one finished session (F8.6). */
data class ExerciseSessionPoint(
    val exerciseName: String,
    val sessionId: String,
    val startedAt: Long,
    val bestWeight: Double,
    val bestReps: Int,
    val volume: Double,
)

@Dao
interface WorkoutSetDao {
    @Upsert
    suspend fun upsertAll(sets: List<WorkoutSetEntity>)

    @Upsert
    suspend fun upsert(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getById(id: String): WorkoutSetEntity?

    @Query(
        "SELECT w.*, e.name AS exerciseName, e.sort_order AS exerciseOrder FROM workout_sets w " +
            "JOIN exercises e ON e.id = w.exercise_id " +
            "WHERE w.session_id = :sessionId AND w.deleted = 0 ORDER BY e.sort_order, w.set_no, w.done_at"
    )
    fun observeRowsForSession(sessionId: String): Flow<List<SetRow>>

    /** One point per (exercise name, finished session), oldest first. Timed sets (0 reps) are ignored. */
    @Query(
        "SELECT e.name AS exerciseName, s.id AS sessionId, s.started_at AS startedAt, " +
            "MAX(w.weight) AS bestWeight, MAX(w.reps) AS bestReps, SUM(w.reps * w.weight) AS volume " +
            "FROM workout_sets w JOIN exercises e ON e.id = w.exercise_id JOIN workout_sessions s ON s.id = w.session_id " +
            "WHERE w.deleted = 0 AND w.skipped = 0 AND w.reps > 0 AND s.deleted = 0 AND s.ended_at IS NOT NULL " +
            "GROUP BY LOWER(e.name), s.id ORDER BY s.started_at"
    )
    fun observeExercisePoints(): Flow<List<ExerciseSessionPoint>>

    @Query("SELECT * FROM workout_sets WHERE session_id = :sessionId AND deleted = 0 ORDER BY done_at")
    fun observeForSession(sessionId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE session_id = :sessionId AND deleted = 0 ORDER BY done_at")
    suspend fun getForSession(sessionId: String): List<WorkoutSetEntity>

    /**
     * Local retention: the sets of the sessions [WorkoutSessionDao.pruneOld] is about to delete. Picked by
     * session (not "session missing"), so sets pulled before their session arrives are never deleted.
     */
    @Query(
        """DELETE FROM workout_sets WHERE dirty = 0 AND session_id IN (
             SELECT s.id FROM workout_sessions s WHERE s.ended_at IS NOT NULL AND s.ended_at < :cutoff AND s.dirty = 0
             AND NOT EXISTS (SELECT 1 FROM workout_sets ws WHERE ws.session_id = s.id AND ws.dirty = 1))"""
    )
    suspend fun pruneForOldSessions(cutoff: Long): Int
}

/** Phone-only live workout row (id = 0). */
@Dao
interface ActiveWorkoutDao {
    @Query("SELECT * FROM active_workout WHERE id = 0")
    fun observe(): Flow<ActiveWorkoutEntity?>

    @Query("SELECT * FROM active_workout WHERE id = 0")
    suspend fun get(): ActiveWorkoutEntity?

    @Upsert
    suspend fun upsert(state: ActiveWorkoutEntity)

    @Query("DELETE FROM active_workout")
    suspend fun clear()
}
