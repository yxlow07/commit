package dev.yxuan.commit.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.DataChangeListener
import dev.yxuan.commit.data.repo.TodoRepository
import dev.yxuan.commit.sync.SyncCursors
import dev.yxuan.commit.sync.SyncEngine
import dev.yxuan.commit.sync.SyncRemote
import dev.yxuan.commit.sync.SyncResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/**
 * In-memory stand-in for the Supabase tables: like the Postgres triggers, it stamps `user_id` and a
 * server-clock `updated_at` on every write; pulls are filtered, ordered and paged like PostgREST.
 */
private class FakeServer : SyncRemote {
    val tables = HashMap<String, MutableMap<String, JsonObject>>()
    private var clock = 1_000_000L
    var upserts = 0
    var onUpsert: (suspend (String) -> Unit)? = null

    override suspend fun upsert(table: String, rowsJson: String) {
        upserts++
        onUpsert?.invoke(table)
        val rows = tables.getOrPut(table) { LinkedHashMap() }
        for (row in Json.parseToJsonElement(rowsJson).jsonArray) write(table, row.jsonObject, rows)
    }

    override suspend fun pull(table: String, since: Long, offset: Int, limit: Int): String {
        val rows = tables[table].orEmpty().values
            .filter { it["updated_at"]!!.jsonPrimitive.long > since }
            .sortedWith(compareBy({ it["updated_at"]!!.jsonPrimitive.long }, { it["id"]!!.jsonPrimitive.content }))
            .drop(offset).take(limit)
        return JsonArray(rows).toString()
    }

    /** A change made on the server side (e.g. by the web app). */
    fun webEdit(table: String, id: String, change: Map<String, JsonPrimitive>) {
        val rows = tables.getValue(table)
        write(table, JsonObject(rows.getValue(id) + change), rows)
    }

    fun webInsert(table: String, row: JsonObject) = write(table, row, tables.getOrPut(table) { LinkedHashMap() })

    private fun write(table: String, row: JsonObject, rows: MutableMap<String, JsonObject>) {
        val id = row["id"]!!.jsonPrimitive.content
        val merged = JsonObject((rows[id] ?: emptyMap()) + row + mapOf("user_id" to JsonPrimitive(USER), "updated_at" to JsonPrimitive(++clock)))
        rows[id] = merged
    }

    fun row(table: String, id: String): JsonObject? = tables[table]?.get(id)

    companion object {
        const val USER = "11111111-1111-1111-1111-111111111111"
    }
}

private class MemoryCursors : SyncCursors {
    val map = HashMap<String, Long>()
    override fun get(table: String) = map[table] ?: 0
    override fun set(table: String, value: Long) {
        map[table] = value
    }
}

@RunWith(AndroidJUnit4::class)
class SyncEngineTest {
    private lateinit var db: AppDatabase
    private val server = FakeServer()
    private val cursors = MemoryCursors()
    private lateinit var engine: SyncEngine
    private lateinit var commitments: CommitmentRepository
    private lateinit var todos: TodoRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        engine = SyncEngine(db, server, cursors)
        val noop = DataChangeListener { }
        commitments = CommitmentRepository(db.commitmentDao(), Clock.systemUTC(), noop)
        todos = TodoRepository(db, Clock.systemUTC(), noop)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun pushUploadsDirtyRowsThenTheyAreClean() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val essay = todos.add(study, "Essay", null, recurIntervalDays = 3)
        assertEquals(2, engine.pendingCount())

        val result = engine.sync()
        assertEquals(2, result.pushed)
        assertEquals(0, engine.pendingCount())

        val row = server.row("todos", essay)!!
        assertEquals("Essay", row["title"]!!.jsonPrimitive.content)
        assertEquals(study, row["commitment_id"]!!.jsonPrimitive.content)
        assertEquals(3L, row["recur_interval_days"]!!.jsonPrimitive.long)
        assertTrue("phone-only column must not upload", "dirty" !in row)
        // The pull brought the server's copy back: same server-stamped updated_at and owner locally.
        val local = todos.get(essay)!!
        assertEquals(row["updated_at"]!!.jsonPrimitive.long, local.updatedAt)
        assertEquals(FakeServer.USER, local.userId)

        // Nothing changed: the next round uploads and applies nothing (re-reads don't count).
        assertEquals(SyncResult(0, 0), engine.sync())
    }

    @Test
    fun webEditsArePulledIntoRoomAndFlows() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val essay = todos.add(study, "Essay", null)
        engine.sync()

        server.webEdit("todos", essay, mapOf("title" to JsonPrimitive("Essay v2")))
        server.webInsert(
            "todos",
            buildJsonObject {
                put("id", "web-1"); put("deleted", 0); put("commitment_id", study); put("title", "From the PC")
                put("has_time", 0)
            },
        )
        val result = engine.sync()
        assertTrue(result.pulled >= 2)
        val open = todos.observeOpen(study).first().map { it.title }.toSet()
        assertEquals(setOf("Essay v2", "From the PC"), open)
        assertEquals(0, engine.pendingCount()) // pulled rows are not re-uploaded
    }

    @Test
    fun conflictLastWriteToReachServerWins() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val essay = todos.add(study, "Essay", null)
        engine.sync()

        // Edited on the PC, then on the phone while offline.
        server.webEdit("todos", essay, mapOf("title" to JsonPrimitive("PC title")))
        todos.update(essay, "Phone title", study, null)
        engine.sync()

        // The phone's edit reached the server last, so it is the newest everywhere.
        assertEquals("Phone title", server.row("todos", essay)!!["title"]!!.jsonPrimitive.content)
        assertEquals("Phone title", todos.get(essay)!!.title)
    }

    @Test
    fun editDuringUploadStaysPendingAndUploadsNext() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val essay = todos.add(study, "Essay", null)
        // While the batch is on its way, the user renames the todo.
        server.onUpsert = { table -> if (table == "todos") { server.onUpsert = null; todos.update(essay, "Renamed", study, null) } }
        engine.sync()
        assertEquals("Renamed", todos.get(essay)!!.title) // the pull didn't overwrite the newer local edit
        assertEquals(1, engine.pendingCount())

        engine.sync()
        assertEquals("Renamed", server.row("todos", essay)!!["title"]!!.jsonPrimitive.content)
        assertEquals(0, engine.pendingCount())
    }

    @Test
    fun deletesSyncAsSoftDeletesBothWays() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        val a = todos.add(study, "A", null)
        val b = todos.add(study, "B", null)
        engine.sync()

        todos.delete(a)
        server.webEdit("todos", b, mapOf("deleted" to JsonPrimitive(1)))
        engine.sync()

        assertEquals(1L, server.row("todos", a)!!["deleted"]!!.jsonPrimitive.long)
        assertTrue(todos.observeOpen(study).first().isEmpty())
        assertTrue(todos.get(b)!!.deleted)
    }

    @Test
    fun largePullIsPagedAndCursorAdvances() = runTest {
        val study = commitments.create("Study", CommitmentType.STANDARD, 0)
        engine.sync()
        repeat(1_200) { i ->
            server.webInsert(
                "todos",
                buildJsonObject { put("id", "bulk-$i"); put("deleted", 0); put("commitment_id", study); put("title", "Todo $i"); put("has_time", 0) },
            )
        }
        assertEquals(1_200, engine.sync().pulled)
        assertEquals(1_200, todos.observeOpen(study).first().size)
        val newest = server.tables.getValue("todos").values.maxOf { it["updated_at"]!!.jsonPrimitive.long }
        assertEquals(newest, cursors.get("todos"))

        // Next round re-reads the short overlap window but changes nothing.
        val again = engine.sync()
        assertEquals(0, again.pushed)
        assertEquals(0, again.pulled)
        assertNull(todos.get("missing"))
    }
}
