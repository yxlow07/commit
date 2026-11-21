package dev.yxuan.commit.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.ExerciseSessionPoint
import dev.yxuan.commit.data.db.dao.SessionSummary
import dev.yxuan.commit.data.db.dao.SetRow
import dev.yxuan.commit.data.repo.HistoryRepository
import dev.yxuan.commit.ui.SessionDetailRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** Weeks shown in the heatmap and loaded for the task timeline. */
const val HISTORY_WEEKS = 26

private fun <T> ViewModel.state(flow: Flow<T>, initial: T): StateFlow<T> =
    flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

class HistoryViewModel(repo: HistoryRepository, val clock: Clock) : ViewModel() {
    private val since = LocalDate.now(clock).minusWeeks(HISTORY_WEEKS.toLong()).atStartOfDay(clock.zone).toInstant().toEpochMilli()

    val completions: StateFlow<List<CompletionRow>?> = state(repo.observeCompletionsSince(since), null)
    val sessions: StateFlow<List<SessionSummary>?> = state(repo.observeSessions(), null)
    val exercisePoints: StateFlow<List<ExerciseSessionPoint>> = state(repo.observeExercisePoints(), emptyList())
}

class SessionDetailViewModel(savedStateHandle: SavedStateHandle, private val repo: HistoryRepository) : ViewModel() {
    private val id = savedStateHandle.toRoute<SessionDetailRoute>().id
    val session: StateFlow<SessionSummary?> = state(repo.observeSession(id), null)
    val sets: StateFlow<List<SetRow>> = state(repo.observeSessionSets(id), emptyList())

    fun edit(setId: String, reps: Int, weight: Double) {
        viewModelScope.launch { repo.editSet(setId, reps, weight) }
    }

    fun delete(setId: String) {
        viewModelScope.launch { repo.deleteSet(setId) }
    }
}
