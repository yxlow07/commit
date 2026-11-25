package dev.yxuan.commit.data.db.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * Every synced table carries the same sync columns (see PRD "Data model"):
 *  - id:         client-generated UUID
 *  - user_id:    owner; null until the user signs in (M7)
 *  - updated_at: epoch ms; local clock for now, set by a Postgres trigger once synced
 *  - deleted:    soft-delete flag, so deletes sync like any other change
 *  - dirty:      phone-only "not pushed yet" flag, set on every local write and cleared by sync
 * All timestamps are epoch milliseconds (UTC).
 */

/** STANDARD holds todos, WORKOUT routines, COLLECTION a standalone collection tracker (groups of items). */
enum class CommitmentType { STANDARD, WORKOUT, COLLECTION }

@Entity(
    tableName = "commitments",
    indices = [Index(value = ["user_id", "updated_at"])],
)
data class CommitmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    val name: String,
    val type: CommitmentType = CommitmentType.STANDARD,
    /** ARGB colour. */
    val color: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "show_in_widget") val showInWidget: Boolean = true,
    val archived: Boolean = false,
)

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["user_id", "updated_at"]),
        Index(value = ["user_id", "done_at"]),
        Index(value = ["commitment_id", "due_at"]),
    ],
)
data class TodoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "commitment_id") val commitmentId: String,
    val title: String,
    /** Due instant. When [hasTime] is false only the local date part is meaningful. */
    @ColumnInfo(name = "due_at") val dueAt: Long? = null,
    @ColumnInfo(name = "has_time") val hasTime: Boolean = false,
    /** Completion time for one-off todos. */
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    /** Interval recurrence (M3): due again N days after [lastDoneAt]. */
    @ColumnInfo(name = "recur_interval_days") val recurIntervalDays: Int? = null,
    /** Calendar recurrence (M10): weekday bitmask (Mon = 1 ... Sun = 64). */
    @ColumnInfo(name = "recur_days") val recurDays: Int? = null,
    @ColumnInfo(name = "last_done_at") val lastDoneAt: Long? = null,
    /** Reminder lead time in minutes before [dueAt] (M10). E.g. 0, 15, 30, 60. */
    @ColumnInfo(name = "reminder_lead_minutes") val reminderLeadMinutes: Int? = null,
)

@Entity(
    tableName = "completions",
    indices = [
        Index(value = ["user_id", "updated_at"]),
        Index(value = ["user_id", "done_at"]),
        Index(value = ["todo_id"]),
    ],
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "todo_id") val todoId: String,
    @ColumnInfo(name = "done_at") val doneAt: Long,
)

@Entity(
    tableName = "routines",
    indices = [Index(value = ["user_id", "updated_at"]), Index(value = ["commitment_id"])],
)
data class RoutineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "commitment_id") val commitmentId: String,
    val name: String,
    @ColumnInfo(name = "rest_between_exercises_s") val restBetweenExercisesS: Int = 120,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    /** Weekdays this routine is planned for, as a bitmask (Mon = 1 … Sun = 64). See `domain/Weekdays.kt`. */
    @ColumnInfo(name = "planned_days", defaultValue = "0") val plannedDays: Int = 0,
)

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["user_id", "updated_at"]), Index(value = ["routine_id"])],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "routine_id") val routineId: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weight: Double = 0.0,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    /** Set for timed exercises (e.g. plank). */
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["user_id", "updated_at"]), Index(value = ["routine_id"])],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "workout_sets",
    indices = [
        Index(value = ["user_id", "updated_at"]),
        Index(value = ["user_id", "done_at"]),
        Index(value = ["session_id"]),
        Index(value = ["exercise_id"]),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "set_no") val setNo: Int,
    val reps: Int,
    val weight: Double,
    @ColumnInfo(name = "done_at") val doneAt: Long?,
    val skipped: Boolean = false,
)

@Entity(
    tableName = "themes",
    indices = [Index(value = ["user_id", "updated_at"])],
)
data class ThemeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    val name: String,
    val json: String,
)

/** One row per user. Recap times are minutes since local midnight. */
@Entity(
    tableName = "settings",
    indices = [Index(value = ["user_id", "updated_at"])],
)
data class SettingsEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean = false,
    /** Phone-only: changed locally and not pushed yet. Default 1 so pre-existing rows upload on first sync. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    @ColumnInfo(name = "morning_recap_time") val morningRecapTime: Int = 7 * 60 + 30,
    @ColumnInfo(name = "night_recap_time") val nightRecapTime: Int = 22 * 60,
    @ColumnInfo(name = "morning_recap_enabled") val morningRecapEnabled: Boolean = true,
    @ColumnInfo(name = "night_recap_enabled") val nightRecapEnabled: Boolean = true,
    @ColumnInfo(name = "active_theme_id") val activeThemeId: String? = null,
)

/** Live workout step (F5.2). See `domain/WorkoutEngine.kt`. */
enum class WorkoutPhase { READY, WORK, REST, COMPLETE }

/**
 * Phone-only, not synced: the running workout (PRD "Phone only"). At most one row (id = 0).
 * Everything time-based is a timestamp, so the step is recalculated when drawn.
 */
@Entity(tableName = "active_workout")
data class ActiveWorkoutEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "routine_id") val routineId: String,
    @ColumnInfo(name = "exercise_index") val exerciseIndex: Int,
    /** 1-based set number within the current exercise. */
    @ColumnInfo(name = "set_no") val setNo: Int,
    val phase: WorkoutPhase,
    @ColumnInfo(name = "phase_started_at") val phaseStartedAt: Long,
    /** End of a timed phase (rest, or work for timed exercises); null when waiting for the user. */
    @ColumnInfo(name = "phase_ends_at") val phaseEndsAt: Long?,
    /** Reps and weight as edited on the widget for the current set. */
    val reps: Int,
    val weight: Double,
)
