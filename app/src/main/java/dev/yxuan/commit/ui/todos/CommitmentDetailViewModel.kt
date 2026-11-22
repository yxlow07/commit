package dev.yxuan.commit.ui.todos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.TodoRepository
import dev.yxuan.commit.ui.CommitmentRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommitmentDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val commitments: CommitmentRepository,
    private val todos: TodoRepository,
) : ViewModel() {
    val commitmentId: String = savedStateHandle.toRoute<CommitmentRoute>().id

    val commitment: StateFlow<CommitmentEntity?> =
        commitments.observe(commitmentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val open: StateFlow<List<TodoEntity>?> =
        todos.observeOpen(commitmentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val done: StateFlow<List<TodoEntity>> =
        todos.observeDone(commitmentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDone(id: String, done: Boolean) {
        viewModelScope.launch { todos.setDone(id, done) }
    }

    fun update(name: String, color: Int, showInWidget: Boolean) {
        viewModelScope.launch { commitments.update(commitmentId, name, color, showInWidget) }
    }

    fun move(delta: Int) {
        viewModelScope.launch { commitments.move(commitmentId, delta) }
    }

    fun archive() {
        viewModelScope.launch { commitments.archive(commitmentId) }
    }
}
