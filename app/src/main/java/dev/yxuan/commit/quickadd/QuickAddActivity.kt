package dev.yxuan.commit.quickadd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.Due
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.ui.components.DialogPanel
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.CommitTheme
import dev.yxuan.commit.ui.todos.TodoForm
import dev.yxuan.commit.widget.CommitmentIdParam
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dialog-style popup over the home screen, opened by the widget's add button and the app (F3.4).
 * Translucent theme, so the home screen stays visible behind it. Save writes to Room and closes.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialCommitment = intent.getStringExtra(CommitmentIdParam.name)
        val container = appContainer
        val commitmentsFlow = container.commitmentRepository.observeActive()
            .map { list -> list.filter { it.type == CommitmentType.STANDARD } }
        val themeFlow = container.themeRepository.observeActive()

        setContent {
            val activeTheme by themeFlow.collectAsState(ActiveTheme.Default)
            CommitTheme(active = activeTheme) {
                val commitments by commitmentsFlow.collectAsState(emptyList())
                var title by rememberSaveable { mutableStateOf("") }
                var commitmentId by rememberSaveable { mutableStateOf(initialCommitment) }
                var due by remember { mutableStateOf<Due?>(null) }
                var repeatDays by rememberSaveable { mutableStateOf<Int?>(null) }
                var saving by remember { mutableStateOf(false) }

                // A workout tab (or a stale id) can't hold todos: fall back to the first todo commitment.
                LaunchedEffect(commitments) {
                    if (commitments.isNotEmpty() && commitments.none { it.id == commitmentId }) {
                        commitmentId = commitments.first().id
                    }
                }

                val canSave = title.isNotBlank() && commitmentId != null && !saving
                val save = {
                    val target = commitmentId
                    if (canSave && target != null) {
                        saving = true
                        lifecycleScope.launch {
                            container.todoRepository.add(target, title, due, repeatDays)
                            finish()
                        }
                    }
                }

                QuickAddDialog(onDismiss = ::finish, canSave = canSave, onSave = save) { focus ->
                    TodoForm(
                        title = title,
                        onTitleChange = { title = it },
                        commitments = commitments,
                        commitmentId = commitmentId,
                        onCommitmentChange = { commitmentId = it },
                        due = due,
                        onDueChange = { due = it },
                        repeatDays = repeatDays,
                        onRepeatChange = { repeatDays = it },
                        compact = true,
                        titleFocusRequester = focus,
                        onDone = save,
                    )
                }
            }
        }
    }
}

/**
 * The spec's quick-add popup: `scrim` over the home screen, a `surface` panel (theme radius, 20 dp
 * sides, 24 dp top). Tapping the scrim or Back cancels; Save is disabled until the title has a character.
 */
@Composable
private fun QuickAddDialog(
    onDismiss: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Commit.colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        DialogPanel(
            title = "New todo",
            actions = {
                TextAction("Cancel", onClick = onDismiss)
                PrimaryButton("Save todo", onClick = onSave, enabled = canSave)
            },
            modifier = Modifier
                .widthIn(max = 480.dp)
                // Swallow taps on the panel so they don't dismiss.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            content(focus)
        }
    }
}
