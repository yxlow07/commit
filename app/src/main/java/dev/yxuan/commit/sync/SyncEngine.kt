package dev.yxuan.commit.sync

import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.room3.immediateTransaction
import dev.yxuan.commit.data.db.AppDatabase

/** The server side of sync (Supabase PostgREST in the app, a fake in tests). Rows travel as JSON arrays. */
interface SyncRemote {
    /** Inserts or updates [rowsJson] (a JSON array of row objects) in [table]. */
    suspend fun upsert(table: String, rowsJson: String)

    /** Rows of [table] with `updated_at > since`, ordered by `updated_at, id`, as a JSON array. */
    suspend fun pull(table: String, since: Long, offset: Int, limit: Int): String
}

/** Remembers, per table, the newest server `updated_at` already pulled (`last_synced_at`). */
interface SyncCursors {
    fun get(table: String): Long
    fun set(table: String, value: Long)
}

/** [pulled] counts rows that changed locally (re-reads of already-synced rows don't count). */
data class SyncResult(val pushed: Int, val pulled: Int)

/**
 * Hand-written sync (PRD "Sync"), table-agnostic: rows are turned into JSON and back with SQLite's
 * JSON functions, using each table's columns, so adding a column needs no sync code.
 *
 * 1. Every local write sets `dirty = 1` (repositories, DAOs).
 * 2. Push: dirty rows are upserted on the server, then marked clean, but only if they weren't
 *    edited again meanwhile (same `updated_at`), so an edit during the upload is pushed next time.
 * 3. Pull: rows with server `updated_at > last_synced_at` are applied locally and the cursor moves on.
 * 4. Conflicts: the server stamps `updated_at` with its own clock, so the last write to reach it is
 *    the newest. A local row with unpushed edits is not overwritten by a pull: it is pushed next
 *    and becomes the newest.
 * 5. Deletes are soft (`deleted = 1`), so they sync like any other change.
 * Every step is idempotent, so a sync can be retried at any point.
 */
class SyncEngine(
    private val db: AppDatabase,
    private val remote: SyncRemote,
    private val cursors: SyncCursors,
) {
    private val columns = HashMap<String, List<String>>()

    suspend fun sync(): SyncResult {
        var pushed = 0
        var pulled = 0
        for (table in TABLES) pushed += push(table)
        for (table in TABLES) pulled += pull(table)
        return SyncResult(pushed, pulled)
    }

    /** Number of rows waiting to be pushed, for the status line. */
    suspend fun pendingCount(): Int = db.useReaderConnection { c ->
        TABLES.sumOf { table -> c.usePrepared("SELECT COUNT(*) FROM \"$table\" WHERE dirty = 1") { st -> st.step(); st.getLong(0).toInt() } }
    }

    private suspend fun push(table: String): Int {
        val cols = columns(table)
        val json = cols.joinToString(", ") { "'$it', \"$it\"" }
        var total = 0
        repeat(MAX_BATCHES) {
            val batch = db.useReaderConnection { c ->
                c.usePrepared("SELECT id, updated_at, json_object($json) FROM \"$table\" WHERE dirty = 1 ORDER BY updated_at LIMIT $PUSH_BATCH") { st ->
                    buildList { while (st.step()) add(Triple(st.getText(0), st.getLong(1), st.getText(2))) }
                }
            }
            if (batch.isEmpty()) return total
            remote.upsert(table, batch.joinToString(",", "[", "]") { it.third })
            db.useWriterConnection { c ->
                c.immediateTransaction {
                    for ((id, updatedAt, _) in batch) {
                        usePrepared("UPDATE \"$table\" SET dirty = 0 WHERE id = ? AND updated_at = ?") { st ->
                            st.bindText(1, id)
                            st.bindLong(2, updatedAt)
                            st.step()
                        }
                    }
                }
            }
            total += batch.size
            if (batch.size < PUSH_BATCH) return total
        }
        return total
    }

    private suspend fun pull(table: String): Int {
        val cols = columns(table)
        val cursor = cursors.get(table)
        // Re-read a short window before the cursor: a row committed late can carry a slightly older
        // server timestamp. Applying a row twice is harmless.
        val since = maxOf(0L, cursor - OVERLAP_MS)
        var newest = cursor
        var offset = 0
        var total = 0
        while (true) {
            val page = remote.pull(table, since, offset, PULL_PAGE)
            val (count, maxUpdated) = db.useReaderConnection { c ->
                c.usePrepared("SELECT COUNT(*), MAX(json_extract(value, '$.updated_at')) FROM json_each(?)") { st ->
                    st.bindText(1, page)
                    st.step()
                    st.getLong(0).toInt() to (if (st.isNull(1)) 0L else st.getLong(1))
                }
            }
            if (count == 0) break
            total += apply(table, cols, page)
            newest = maxOf(newest, maxUpdated)
            offset += count
            if (count < PULL_PAGE) break
        }
        if (newest != cursor) cursors.set(table, newest)
        return total
    }

    /**
     * Upserts a JSON array of server rows and returns how many rows actually changed. Skips local
     * rows that still have unpushed edits, and rows already at this server version (re-reads).
     */
    private suspend fun apply(table: String, cols: List<String>, rowsJson: String): Int {
        val names = cols.joinToString(", ") { "\"$it\"" }
        val values = cols.joinToString(", ") { "json_extract(j.value, '$.$it')" }
        val updates = cols.filter { it != "id" }.joinToString(", ") { "\"$it\" = excluded.\"$it\"" }
        // `WHERE true` keeps SQLite from reading ON CONFLICT as part of the SELECT.
        val sql = "INSERT INTO \"$table\" ($names, dirty) SELECT $values, 0 FROM json_each(?) AS j WHERE true " +
            "ON CONFLICT(id) DO UPDATE SET $updates, dirty = 0 " +
            "WHERE \"$table\".dirty = 0 AND \"$table\".updated_at IS NOT excluded.updated_at"
        return db.useWriterConnection { c ->
            c.immediateTransaction {
                usePrepared(sql) { st ->
                    st.bindText(1, rowsJson)
                    st.step()
                }
                usePrepared("SELECT changes()") { st -> st.step(); st.getLong(0).toInt() }
            }
        }
    }

    /** Synced columns of [table]: everything except the phone-only `dirty` flag. */
    private suspend fun columns(table: String): List<String> = columns.getOrPut(table) {
        db.useReaderConnection { c ->
            c.usePrepared("PRAGMA table_info(\"$table\")") { st ->
                buildList { while (st.step()) add(st.getText(1)) }
            }
        }.filter { it != "dirty" }
    }

    companion object {
        /** Synced tables, parents first. `active_workout` is phone-only. */
        val TABLES = listOf(
            "commitments", "todos", "completions", "routines", "exercises",
            "workout_sessions", "workout_sets", "themes", "settings",
        )
        const val PUSH_BATCH = 200
        const val PULL_PAGE = 500
        const val OVERLAP_MS = 5 * 60_000L
        private const val MAX_BATCHES = 50
    }
}
