package dev.yxuan.commit.data

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yxuan.commit.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema version up to the current one migrates with user data intact (Room 3 helper). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val file = instrumentation.targetContext.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, file, BundledSQLiteDriver(), AppDatabase::class)

    @Test
    fun migrate1To5KeepsData() = runTest {
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO commitments (id, user_id, updated_at, deleted, dirty, name, type, color, sort_order, show_in_widget, archived) " +
                    "VALUES ('c1', NULL, 1, 0, 1, 'Study', 'standard', 0, 0, 1, 0)"
            )
            v1.execSQL(
                "INSERT INTO todos (id, user_id, updated_at, deleted, dirty, commitment_id, title, due_at, has_time, done_at, recur_interval_days, last_done_at) " +
                    "VALUES ('t1', NULL, 1, 0, 1, 'c1', 'Essay', NULL, 0, NULL, 3, 99)"
            )
            v1.execSQL(
                "INSERT INTO routines (id, user_id, updated_at, deleted, dirty, commitment_id, name, rest_between_exercises_s, sort_order) " +
                    "VALUES ('r1', NULL, 1, 0, 1, 'c1', 'Push Day', 120, 0)"
            )
        }
        helper.runMigrationsAndValidate(5).use { v5 ->
            assertEquals("Study", v5.single("SELECT name FROM commitments WHERE id = 'c1'"))
            assertEquals("Essay|3|99", v5.single("SELECT title || '|' || recur_interval_days || '|' || last_done_at FROM todos"))
            // v3 column added with its default; v2 table present; v4 dropped `dirty`, v5 re-added it
            // with default 1, so every existing row uploads on the first sync.
            assertEquals("0", v5.single("SELECT planned_days FROM routines"))
            assertEquals("0", v5.single("SELECT COUNT(*) FROM active_workout"))
            assertEquals("1", v5.single("SELECT dirty FROM todos"))
            for (table in listOf("commitments", "todos", "completions", "routines", "exercises", "workout_sessions", "workout_sets", "themes", "settings")) {
                assertTrue("$table has no dirty", v5.columns(table).contains("dirty"))
                assertTrue("$table lost updated_at", v5.columns(table).contains("updated_at"))
            }
        }
    }

    private fun SQLiteConnection.single(sql: String): String = prepare(sql).use { st ->
        assertTrue("no row for $sql", st.step())
        st.getText(0)
    }

    private fun SQLiteConnection.columns(table: String): List<String> = prepare("PRAGMA table_info($table)").use { st ->
        buildList { while (st.step()) add(st.getText(1)) }
    }
}
