package dev.yxuan.commit.data.repo

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
    suspend fun build(kind: RecapKind): Recap {
        val today = LocalDate.now(clock)
        val start = today.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val input = RecapInput(
            openTodos = db.todoDao().getOpenInActiveCommitments(),
            commitments = db.commitmentDao().getActive(),
            routines = db.routineDao().getAll(),
            completedToday = db.completionDao().rowsBetween(start, end),
            sessionsToday = db.workoutSessionDao().summariesEndedBetween(start, end),
        )
        return RecapBuilder(clock).build(kind, input)
    }
}
