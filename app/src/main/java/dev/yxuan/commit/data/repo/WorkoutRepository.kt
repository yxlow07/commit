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

    /** Sets actually done (skipped ones left out), for "4 sets done" and summaries. */
    val doneSets: List<WorkoutSetEntity> get() = sets.filter { !it.skipped }

    /** Sets done per exercise id this session. */
    val doneCounts: Map<String, Int> get() = WorkoutEngine.doneCounts(sets)

    fun doneOf(index: Int): Int = exercises.getOrNull(index)?.let { doneCounts[it.id] ?: 0 } ?: 0
    fun remainingOf(index: Int): Int = exercises.getOrNull(index)?.let { it.sets - doneOf(index) } ?: 0

    /** The exercise after the current one that still has sets left (wrapping), as the engine picks it. */
    val nextExercise: ExerciseEntity?
        get() = (1..exercises.size).map { (state.exerciseIndex + it) % exercises.size }
            .firstOrNull { it != state.exerciseIndex && remainingOf(it) > 0 }?.let { exercises[it] }

    /** The current exercise still has sets after the one just logged (during a rest). */
    private val moreOfThis: Boolean get() = remainingOf(state.exerciseIndex) > 0

    /** The current exercise's planned sets are done and it is resting or the workout is done: "One more set" applies. */
    val canExtraSet: Boolean
        get() = exercise != null && remainingOf(state.exerciseIndex) <= 0 &&
            (state.phase == WorkoutPhase.REST || state.phase == WorkoutPhase.COMPLETE)

    /** What ends the rest: "Next exercise" after an exercise's last set, otherwise "Next set". */
    val nextLabel: String
        get() = if (moreOfThis) "Next set" else "Next exercise"

    /** The rest has run out and is waiting for "Next set" (shown as red overtime). */
    fun restOver(now: Long): Boolean = state.phase == WorkoutPhase.REST && (state.phaseEndsAt ?: Long.MAX_VALUE) <= now

    /** What comes after the current rest, e.g. "Bench press, set 3". */
    val upNext: String?
        get() {
            val ex = exercise ?: return null
            return if (moreOfThis) "${ex.name}, set ${doneOf(state.exerciseIndex) + 1}" else nextExercise?.name
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

    /** Do another exercise next (any with sets left), between sets or during a rest. */
    suspend fun choose(exerciseIndex: Int) = mutate { engine, s, now -> engine.choose(s, exerciseIndex, now) }
    suspend fun extraSet() = mutate { engine, s, now -> engine.extraSet(s, now) }
    suspend fun skipSet() = mutate { engine, s, now -> engine.skipSet(s, now) }
    suspend fun endExercise() = mutate { engine, s, now -> engine.endExercise(s, now) }
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
        // Alert when the workout moved on, or when a rest has just run out (it now waits in overtime).
        val now = clock.millis()
        val restJustEnded = live.restOver(now) && now - (live.state.phaseEndsAt ?: now) < 60_000
        if (live.state != before || restJustEnded) effects.alert(live)
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
                val step = WorkoutEngine(live.exercises, live.routine.restBetweenExercisesS, live.doneCounts).resolve(state, clock.millis())
                writeSets(state.sessionId, step)
            }
            val session = sessions.getById(state.sessionId)
            if (session != null) {
                val now = clock.millis()
                // Only skipped sets ("Skip set", "End exercise") is nothing done: no session in History.
                val hasSets = setsDao.getForSession(state.sessionId).any { !it.skipped } || resolved == true
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
            val done = WorkoutEngine.doneCounts(setsDao.getForSession(state.sessionId))
            val engine = WorkoutEngine(exercises, routine.restBetweenExercisesS, done)
            // Always replay ended timers first, so a tap after a missed alarm acts on the real step.
            val resolved = engine.resolve(state, now)
            val changed = change(engine, resolved.state, now)
            writeSets(state.sessionId, Step(changed.state, resolved.logged + changed.logged))
            if (changed.state.phase == WorkoutPhase.COMPLETE && state.phase != WorkoutPhase.COMPLETE) {
                sessions.getById(state.sessionId)?.let {
                    sessions.upsert(it.copy(endedAt = changed.state.phaseStartedAt, updatedAt = now, dirty = true))
                }
            } else if (state.phase == WorkoutPhase.COMPLETE && changed.state.phase != WorkoutPhase.COMPLETE) {
                // "One more set" after the workout was done: running again, so not in History yet.
                sessions.getById(state.sessionId)?.let {
                    sessions.upsert(it.copy(endedAt = null, updatedAt = now, dirty = true))
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
                skipped = it.skipped,
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
        // Only future ends: a rest in overtime has its end in the past, and arming that would fire at once, forever.
        effects.setTimer(live.state.phaseEndsAt?.takeIf { it > clock.millis() })
        effects.changed(live)
    }
}
