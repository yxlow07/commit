package dev.yxuan.commit.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.yxuan.commit.data.db.dao.ActiveWorkoutDao
import dev.yxuan.commit.data.db.dao.CollectionDao
import dev.yxuan.commit.data.db.dao.CommitmentDao
import dev.yxuan.commit.data.db.dao.CompletionDao
import dev.yxuan.commit.data.db.dao.ExerciseDao
import dev.yxuan.commit.data.db.dao.RoutineDao
import dev.yxuan.commit.data.db.dao.SettingsDao
import dev.yxuan.commit.data.db.dao.ThemeDao
import dev.yxuan.commit.data.db.dao.TodoDao
import dev.yxuan.commit.data.db.dao.WorkoutSessionDao
import dev.yxuan.commit.data.db.dao.WorkoutSetDao
import dev.yxuan.commit.data.db.entity.ActiveWorkoutEntity
import dev.yxuan.commit.data.db.entity.CollectionGroupEntity
import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CompletionEntity
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.db.entity.SettingsEntity
import dev.yxuan.commit.data.db.entity.ThemeEntity
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.data.db.entity.WorkoutSessionEntity
import dev.yxuan.commit.data.db.entity.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        CommitmentEntity::class,
        TodoEntity::class,
        CompletionEntity::class,
        RoutineEntity::class,
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        ThemeEntity::class,
        SettingsEntity::class,
        ActiveWorkoutEntity::class,
        CollectionGroupEntity::class,
        CollectionItemEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // M4: phone-only active_workout table
        AutoMigration(from = 2, to = 3), // M5: routines.planned_days
        AutoMigration(from = 3, to = 4, spec = DropDirtyColumns::class), // M7 (PowerSync, later removed)
        AutoMigration(from = 4, to = 5), // M7: hand-written sync, `dirty` back (default 1 = upload everything)
        AutoMigration(from = 5, to = 6), // M10: todos.recur_days, todos.reminder_lead_minutes
        AutoMigration(from = 6, to = 7), // Collections: collection_groups, collection_items
    ],
)
@ColumnTypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun commitmentDao(): CommitmentDao
    abstract fun todoDao(): TodoDao
    abstract fun completionDao(): CompletionDao
    abstract fun routineDao(): RoutineDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun themeDao(): ThemeDao
    abstract fun settingsDao(): SettingsDao
    abstract fun activeWorkoutDao(): ActiveWorkoutDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val FILE_NAME = "commit.db"

        /**
         * Opens the app database. Room 3 needs a [SQLiteDriver]; the bundled driver ships its own
         * SQLite, so behaviour (including JSON functions used by sync) is the same on every device.
         */
        fun build(context: Context, driver: SQLiteDriver = BundledSQLiteDriver()): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
    }
}

/** v3 → v4 dropped `dirty` for PowerSync; v5 re-adds it for the hand-written sync. */
@DeleteColumn(tableName = "commitments", columnName = "dirty")
@DeleteColumn(tableName = "todos", columnName = "dirty")
@DeleteColumn(tableName = "completions", columnName = "dirty")
@DeleteColumn(tableName = "routines", columnName = "dirty")
@DeleteColumn(tableName = "exercises", columnName = "dirty")
@DeleteColumn(tableName = "workout_sessions", columnName = "dirty")
@DeleteColumn(tableName = "workout_sets", columnName = "dirty")
@DeleteColumn(tableName = "themes", columnName = "dirty")
@DeleteColumn(tableName = "settings", columnName = "dirty")
class DropDirtyColumns : AutoMigrationSpec
