package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.DeleteIconButton
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Space

/** Full todo editor (pushed): the quick-add fields plus weekday repeat and reminder lead time. */
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

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PushedTopBar(onBack = onDone) {
            if (!viewModel.isNew) DeleteIconButton(onClick = viewModel::delete, contentDescription = "Delete todo")
        }
        if (!state.loaded) return@Column
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenSide)
                .padding(top = Space.l, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            ScreenTitle(if (viewModel.isNew) "New todo" else "Edit todo")
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
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.screenSide, vertical = Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.End),
        ) {
            TextAction("Cancel", onClick = onDone)
            PrimaryButton("Save todo", onClick = viewModel::save, enabled = state.canSave)
        }
    }
}
