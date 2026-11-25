package dev.yxuan.commit.data.repo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.SettingsEntity
import dev.yxuan.commit.domain.Recap
import dev.yxuan.commit.domain.RecapBuilder
import dev.yxuan.commit.domain.RecapInput
import dev.yxuan.commit.domain.RecapKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** The single settings row (recap times and toggles). Defaults apply until the first change. */
class SettingsRepository(
    private val db: AppDatabase,
    private val clock: Clock,
    private val listener: DataChangeListener,
) {
    private val dao = db.settingsDao()

    fun observe(): Flow<SettingsEntity> = dao.observe().map { it ?: defaults() }

    suspend fun get(): SettingsEntity = dao.get() ?: defaults()

    suspend fun update(change: (SettingsEntity) -> SettingsEntity) {
        dao.upsert(change(get()).copy(updatedAt = clock.millis(), dirty = true))
        listener.onDataChanged()
    }

    private fun defaults() = SettingsEntity(id = LOCAL_ID, updatedAt = 0)

    companion object {
        /** Id of the settings row before sign-in; re-keyed per user when sync starts (M7). */
        const val LOCAL_ID = "settings"

        /** The one settings row per user, shared by phone and web. */
        fun idFor(userId: String) = "settings-$userId"
    }
}

/** Loads a recap's inputs from Room and builds it (F6.3: always from the local database). */
class RecapRepository(private val db: AppDatabase, private val clock: Clock) {
    suspend fun build(kind: RecapKind): Recap = RecapBuilder(clock).build(kind, input(LocalDate.now(clock)))

    /**
     * The recap as it stands, rebuilt whenever a todo, completion or workout changes, so a recap
     * screen left open (or reopened from the notification) never shows what was true earlier.
     * [day] reviews that day (History grid); null means today's [kind] recap.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(kind: RecapKind, day: LocalDate?): Flow<Recap> =
        db.invalidationTracker.createFlow("todos", "completions", "commitments", "routines", "workout_sessions", "workout_sets")
            .onStart { emit(emptySet()) }
            .mapLatest { if (day != null) buildDay(day) else build(kind) }

    /** A past day's recap, for the History grid. */
    suspend fun buildDay(day: LocalDate): Recap = RecapBuilder(clock).day(day, input(day))

    /** Open todos as they are now, plus [day]'s completions and finished sessions. */
    private suspend fun input(day: LocalDate): RecapInput {
        val start = day.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return RecapInput(
            openTodos = db.todoDao().getOpenInActiveCommitments(),
            commitments = db.commitmentDao().getActive(),
            routines = db.routineDao().getAll(),
            completedToday = db.completionDao().rowsBetween(start, end),
            sessionsToday = db.workoutSessionDao().summariesEndedBetween(start, end),
        )
    }
}
