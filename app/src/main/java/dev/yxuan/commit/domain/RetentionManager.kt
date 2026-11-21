package dev.yxuan.commit.domain

import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.withTransaction
import java.time.Clock
import java.time.LocalDate

/**
 * Manages the 12-month local history retention policy (PRD non-functional requirement).
 * Deletes local clean rows (dirty = 0) older than 365 days from Room so the phone database stays
 * small, while the full history is safely preserved in Supabase for the web app.
 */
object RetentionManager {
    const val RETENTION_DAYS = 365L

    fun cutoffMillis(clock: Clock, days: Long = RETENTION_DAYS): Long {
        val today = LocalDate.now(clock)
        return today.minusDays(days).atStartOfDay(clock.zone).toInstant().toEpochMilli()
    }

    /**
     * Purges synced (`dirty = 0`) completions, finished sessions and their sets older than [days], in one
     * transaction. Plain local deletes: nothing is sent to the server, and the pull cursor has already
     * moved past these rows, so they aren't downloaded again unless edited on the web.
     * Returns the total count of pruned rows.
     */
    suspend fun purgeLocalHistory(db: AppDatabase, clock: Clock, days: Long = RETENTION_DAYS): Int {
        val cutoff = cutoffMillis(clock, days)
        return db.withTransaction {
            val completions = db.completionDao().pruneOld(cutoff)
            val sets = db.workoutSetDao().pruneForOldSessions(cutoff) // before their sessions
            val sessions = db.workoutSessionDao().pruneOld(cutoff)
            completions + sets + sessions
        }
    }
}
