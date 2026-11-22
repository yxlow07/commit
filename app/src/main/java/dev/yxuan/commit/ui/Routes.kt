package dev.yxuan.commit.ui

import kotlinx.serialization.Serializable

/** Today (start). [newCommitment] opens the new-commitment dialog (widget empty state). */
@Serializable
data class TodayRoute(val newCommitment: Boolean = false)

@Serializable
object WorkoutsRoute

@Serializable
data class CommitmentRoute(val id: String)

@Serializable
data class RoutineEditorRoute(val id: String)

@Serializable
object LiveWorkoutRoute

@Serializable
object SettingsRoute

@Serializable
object ThemeRoute

@Serializable
object ArchivedRoute

@Serializable
object HistoryRoute

@Serializable
data class SessionDetailRoute(val id: String)

/** Recap screen; [kind] is a `RecapKind` name. */
@Serializable
data class RecapRoute(val kind: String)

/** Edit an existing todo ([todoId] set) or create one in [commitmentId]. */
@Serializable
data class TodoEditorRoute(val todoId: String? = null, val commitmentId: String? = null)
