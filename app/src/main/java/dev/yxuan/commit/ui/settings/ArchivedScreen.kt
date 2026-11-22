package dev.yxuan.commit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchivedViewModel(private val repo: CommitmentRepository) : ViewModel() {
    /** Null until the first load, so the empty message doesn't flash. */
    val archived: StateFlow<List<CommitmentEntity>?> =
        repo.observeArchived().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: String) {
        viewModelScope.launch { repo.restore(id) }
    }
}

/** Settings > Archived: archived commitments, each with Restore. Restored ones return at the end of Today. */
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchivedViewModel = viewModel(factory = AppViewModelFactory),
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val c = Commit.colors

    LazyColumn(modifier.fillMaxSize()) {
        item {
            PushedTopBar(onBack = onBack, backLabel = "Back to Settings")
            Column(
                Modifier.padding(horizontal = Space.screenSide).padding(top = Space.s, bottom = Space.l),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                ScreenTitle("Archived")
                Text("Archived commitments are hidden from Today and the widget. Their todos and history are kept.", style = Commit.type.meta, color = c.muted)
            }
        }
        val list = archived
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    "Nothing archived. Archive a commitment from its Edit dialog.",
                    style = Commit.type.body,
                    color = c.textStrong,
                    modifier = Modifier.padding(horizontal = Space.screenSide, vertical = Space.s),
                )
            }
        }
        items(list.orEmpty(), key = { it.id }) { commitment ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Space.rowApp).padding(horizontal = Space.screenSide),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
                    Text(commitment.name, style = Commit.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (commitment.type == CommitmentType.WORKOUT) "Workouts" else "Todos", style = Commit.type.meta, color = c.muted)
                }
                TextAction("Restore", onClick = { viewModel.restore(commitment.id) })
            }
        }
    }
}
