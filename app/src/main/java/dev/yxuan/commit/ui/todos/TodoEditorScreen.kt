package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.ui.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(
    onDone: () -> Unit,
    viewModel: TodoEditorViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val commitments by viewModel.commitments.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    LaunchedEffect(state.loaded) { if (state.loaded && viewModel.isNew) focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New todo" else "Edit todo") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete todo")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TodoForm(
                title = state.title,
                onTitleChange = viewModel::setTitle,
                commitments = commitments,
                commitmentId = state.commitmentId,
                onCommitmentChange = viewModel::setCommitment,
                due = state.due,
                onDueChange = viewModel::setDue,
                repeatDays = state.repeatDays,
                onRepeatChange = viewModel::setRepeat,
                recurDays = state.recurDays,
                onRecurDaysChange = viewModel::setRecurDays,
                reminderLeadMinutes = state.reminderLeadMinutes,
                onReminderLeadChange = viewModel::setReminderLead,
                titleFocusRequester = focus,
                onDone = viewModel::save,
            )
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
