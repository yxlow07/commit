package dev.yxuan.commit.ui

import kotlinx.serialization.Serializable

/** Today (start). [newCommitment] opens the new-commitment dialog (widget empty state). */
@Serializable
data class TodayRoute(val newCommitment: Boolean = false)

@Serializable
data class CommitmentRoute(val id: String)

@Serializable
data class RoutineEditorRoute(val id: String)

@Serializable
object LiveWorkoutRoute

@Serializable
object ThemeRoute

@Serializable
object ArchivedRoute

@Serializable
data class SessionDetailRoute(val id: String)

/**
 * Recap screen; [kind] is a `RecapKind` name. With [epochDay] (from the History grid) it reviews that
 * day instead: what got done, the workouts, what was due and is still open.
 */
@Serializable
data class RecapRoute(val kind: String, val epochDay: Long = NO_DAY) {
    companion object { const val NO_DAY = Long.MIN_VALUE }
}

/** Edit an existing todo ([todoId] set) or create one in [commitmentId]. */
@Serializable
data class TodoEditorRoute(val todoId: String? = null, val commitmentId: String? = null)

/** A collection (commitment of type COLLECTION): its groups and items. */
@Serializable
data class CollectionRoute(val id: String)
