package dev.yxuan.commit.ui.todos

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.Due
import dev.yxuan.commit.data.repo.TodoRepository
import dev.yxuan.commit.ui.TodoEditorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class TodoEditorState(
    val loaded: Boolean = false,
    val title: String = "",
    val commitmentId: String? = null,
    val due: Due? = null,
    val repeatDays: Int? = null,
    val recurDays: Int? = null,
    val reminderLeadMinutes: Int? = null,
    /** True once saved or deleted; the screen then navigates back. */
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank() && commitmentId != null
}

/** Create or edit a todo: title, due date/time, commitment (move), recurrence, reminder lead time, delete (F2.1, F2.3, F4, F7). */
class TodoEditorViewModel(
    savedStateHandle: SavedStateHandle,
    commitments: CommitmentRepository,
    private val todos: TodoRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TodoEditorRoute>()
    val isNew: Boolean = route.todoId == null

    private val _state = MutableStateFlow(TodoEditorState())
    val state: StateFlow<TodoEditorState> = _state.asStateFlow()

    /** Todo commitments only; workout commitments don't hold todos. */
    val commitments: StateFlow<List<CommitmentEntity>> = commitments.observeActive()
        .map { list -> list.filter { it.type == CommitmentType.STANDARD } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val todo = route.todoId?.let { todos.get(it) }
            _state.value = if (todo != null) {
                TodoEditorState(
                    loaded = true,
                    title = todo.title,
                    commitmentId = todo.commitmentId,
                    due = Due.of(todo, clock),
                    repeatDays = todo.recurIntervalDays,
                    recurDays = todo.recurDays,
                    reminderLeadMinutes = todo.reminderLeadMinutes,
                )
            } else {
                TodoEditorState(loaded = true, commitmentId = route.commitmentId)
            }
        }
    }

    fun setTitle(title: String) = _state.update { it.copy(title = title) }
    fun setCommitment(id: String) = _state.update { it.copy(commitmentId = id) }
    fun setDue(due: Due?) = _state.update { it.copy(due = due) }
    fun setRepeat(days: Int?) = _state.update { it.copy(repeatDays = days) }
    fun setRecurDays(days: Int?) = _state.update { it.copy(recurDays = days) }
    fun setReminderLead(minutes: Int?) = _state.update { it.copy(reminderLeadMinutes = minutes) }

    fun save() {
        val s = _state.value
        val commitmentId = s.commitmentId ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val id = route.todoId
            if (id == null) todos.add(commitmentId, s.title, s.due, s.repeatDays, s.recurDays, s.reminderLeadMinutes)
            else todos.update(id, s.title, commitmentId, s.due, s.repeatDays, s.recurDays, s.reminderLeadMinutes)
            _state.update { it.copy(finished = true) }
        }
    }

    fun delete() {
        val id = route.todoId ?: return
        viewModelScope.launch {
            todos.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }
}
