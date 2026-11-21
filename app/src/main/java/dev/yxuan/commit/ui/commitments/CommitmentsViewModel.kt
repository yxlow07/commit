package dev.yxuan.commit.ui.commitments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.CommitmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommitmentsViewModel(private val repo: CommitmentRepository) : ViewModel() {

    /** null until the first load, so the screen can tell "loading" from "empty". */
    val commitments: StateFlow<List<CommitmentEntity>?> =
        repo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, type: CommitmentType, color: Int) = launch { repo.create(name, type, color) }

    fun update(id: String, name: String, color: Int, showInWidget: Boolean) =
        launch { repo.update(id, name, color, showInWidget) }

    fun archive(id: String) = launch { repo.archive(id) }

    fun move(id: String, delta: Int) = launch { repo.move(id, delta) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
