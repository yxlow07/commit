package dev.yxuan.commit.data.repo

import dev.yxuan.commit.data.db.withTransaction
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.ActiveWorkoutEntity
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.db.entity.WorkoutSessionEntity
import dev.yxuan.commit.data.db.entity.WorkoutSetEntity
import dev.yxuan.commit.domain.Step
import dev.yxuan.commit.domain.WorkoutEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.util.UUID

/** Everything the widget, notification and app need to draw the running workout. */
data class LiveWorkout(
    val state: ActiveWorkoutEntity,
    val routine: RoutineEntity,
    val exercises: List<ExerciseEntity>,
    val sets: List<WorkoutSetEntity>,
    val startedAt: Long,
) {
    val exercise: ExerciseEntity? get() = exercises.getOrNull(state.exerciseIndex)
    val isTimed: Boolean get() = (exercise?.durationSeconds ?: 0) > 0
    val volume: Double get() = WorkoutEngine.volume(sets.map { it.reps to it.weight })

    /** What comes after the current rest, e.g. "Bench press · set 3". */
    val upNext: String?
        get() {
            val ex = exercise ?: return null
            return if (state.setNo < ex.sets) "${ex.name} · set ${state.setNo + 1}"
            else exercises.getOrNull(state.exerciseIndex + 1)?.name
        }
}

/** Side effects of a workout change, implemented with Android services in `AppContainer`. */
interface WorkoutEffects {
    /** Arm the timer alarm for [at], or cancel it when null. */
    fun setTimer(at: Long?)

    /** Redraw widget and ongoing notification for the new state (null = no workout). */
    suspend fun changed(live: LiveWorkout?)

    /** A timer ended and the workout moved on: vibrate and sound (F5.6). */
    fun alert(live: LiveWorkout)
}

/**
 * Runs workouts (F5): every change goes through [WorkoutEngine], logs sets with the actual
 * values, keeps the phone-only `active_workout` row, and arms the next timer.
 */
class WorkoutRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val effects: WorkoutEffects,
) {
    private val active = db.activeWorkoutDao()
    private val sessions = db.workoutSessionDao()
    private val setsDao = db.workoutSetDao()
    private val routines = db.routineDao()
    private val exercisesDao = db.exerciseDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLive(): Flow<LiveWorkout?> = active.observe().flatMapLatest { state ->
        if (state == null) return@flatMapLatest flowOf(null)
        combine(
            routines.observeById(state.routineId),
            exercisesDao.observeForRoutine(state.routineId),
            setsDao.observeForSession(state.sessionId),
        ) { routine, exercises, sets ->
            routine?.let { LiveWorkout(state, it, exercises, sets, sessions.getById(state.sessionId)?.startedAt ?: state.phaseStartedAt) }
        }
    }

    suspend fun getLive(): LiveWorkout? {
        val state = active.get() ?: return null
        return load(state)
    }

    /** Starts a routine (F5.2). A workout already running is ended first. */
    suspend fun start(routineId: String) {
        if (active.get() != null) end()
        val live = db.withTransaction {
            val routine = routines.getById(routineId) ?: return@withTransaction null
            val exercises = exercisesDao.getForRoutine(routineId)
            if (exercises.isEmpty()) return@withTransaction null
            val now = clock.millis()
            val sessionId = UUID.randomUUID().toString()
            sessions.upsert(WorkoutSessionEntity(id = sessionId, updatedAt = now, dirty = true, routineId = routineId, startedAt = now))
            val state = WorkoutEngine(exercises, routine.restBetweenExercisesS).start(sessionId, routineId, now)
            active.upsert(state)
            LiveWorkout(state, routine, exercises, emptyList(), now)
        } ?: return
        publish(live)
    }

    suspend fun primary() = mutate { engine, s, now -> engine.primary(s, now) }
    suspend fun skip() = mutate { engine, s, now -> engine.skip(s, now) }
    suspend fun addRest() = mutate { engine, s, _ -> Step(engine.addRest(s, WorkoutEngine.ADD_REST_SECONDS)) }
    suspend fun stepReps(delta: Int) = mutate { engine, s, _ -> Step(engine.adjustReps(s, delta)) }
    suspend fun stepWeight(delta: Int) =
        mutate { engine, s, _ -> Step(engine.adjustWeight(s, delta * WorkoutEngine.WEIGHT_STEP_KG)) }

    /**
     * A timer alarm fired, or alarms are being restored after boot: replay ended timers,
     * alert if the workout moved on, and arm the next timer.
     */
    suspend fun onTimer() {
        val before = active.get() ?: return
        val live = mutate { engine, s, now -> engine.resolve(s, now) } ?: return
        if (live.state != before) effects.alert(live)
    }

    /**
     * Ends the workout (F5.8): saves the end time, or discards the session if no set was done.
     * Also used to dismiss the "Workout complete" summary.
     */
    suspend fun end() {
        db.withTransaction {
            val state = active.get() ?: return@withTransaction
            // Count any timers that already ran out before ending.
            val resolved = load(state)?.let { live ->
                val step = WorkoutEngine(live.exercises, live.routine.restBetweenExercisesS).resolve(state, clock.millis())
                writeSets(state.sessionId, step)
            }
            val session = sessions.getById(state.sessionId)
            if (session != null) {
                val now = clock.millis()
                val hasSets = setsDao.getForSession(state.sessionId).isNotEmpty() || resolved == true
                sessions.upsert(
                    if (hasSets) session.copy(endedAt = session.endedAt ?: now, updatedAt = now, dirty = true)
                    else session.copy(deleted = true, updatedAt = now, dirty = true)
                )
            }
            active.clear()
        }
        effects.setTimer(null)
        effects.changed(null)
    }

    private suspend fun mutate(change: (WorkoutEngine, ActiveWorkoutEntity, Long) -> Step): LiveWorkout? {
        val live = db.withTransaction {
            val state = active.get() ?: return@withTransaction null
            val routine = routines.getById(state.routineId) ?: return@withTransaction null
            val exercises = exercisesDao.getForRoutine(state.routineId)
            val now = clock.millis()
            val engine = WorkoutEngine(exercises, routine.restBetweenExercisesS)
            // Always replay ended timers first, so a tap after a missed alarm acts on the real step.
            val resolved = engine.resolve(state, now)
            val changed = change(engine, resolved.state, now)
            writeSets(state.sessionId, Step(changed.state, resolved.logged + changed.logged))
            if (changed.state.phase == WorkoutPhase.COMPLETE && state.phase != WorkoutPhase.COMPLETE) {
                sessions.getById(state.sessionId)?.let {
                    sessions.upsert(it.copy(endedAt = changed.state.phaseStartedAt, updatedAt = now, dirty = true))
                }
            }
            if (changed.state != state) active.upsert(changed.state)
            load(changed.state)
        } ?: return null
        publish(live)
        return live
    }

    /** Writes logged sets; returns true if any were written. */
    private suspend fun writeSets(sessionId: String, step: Step): Boolean {
        if (step.logged.isEmpty()) return false
        val now = clock.millis()
        setsDao.upsertAll(step.logged.map {
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(), updatedAt = now, dirty = true, sessionId = sessionId,
                exerciseId = it.exerciseId, setNo = it.setNo, reps = it.reps, weight = it.weight, doneAt = it.doneAt,
            )
        })
        return true
    }

    private suspend fun load(state: ActiveWorkoutEntity): LiveWorkout? {
        val routine = routines.getById(state.routineId) ?: return null
        val startedAt = sessions.getById(state.sessionId)?.startedAt ?: state.phaseStartedAt
        return LiveWorkout(state, routine, exercisesDao.getForRoutine(state.routineId), setsDao.getForSession(state.sessionId), startedAt)
    }

    private suspend fun publish(live: LiveWorkout) {
        effects.setTimer(live.state.phaseEndsAt)
        effects.changed(live)
    }
}
