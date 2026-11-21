package dev.yxuan.commit.quickadd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.yxuan.commit.ui.theme.CommitTheme
import dev.yxuan.commit.ui.todos.TodoForm
import dev.yxuan.commit.widget.CommitmentIdParam
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dialog-style popup over the home screen, opened by the widget's "+ Add" button (F3.4).
 * Translucent theme, so the home screen stays visible behind it. Save writes to Room and closes.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialCommitment = intent.getStringExtra(CommitmentIdParam.name)
        val container = appContainer
        val commitmentsFlow = container.commitmentRepository.observeActive()
            .map { list -> list.filter { it.type == CommitmentType.STANDARD } }

        setContent {
            CommitTheme {
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

                QuickAddDialog(
                    onDismiss = ::finish,
                    canSave = canSave,
                    onSave = save,
                ) { focus ->
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
                        titleFocusRequester = focus,
                        onDone = save,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAddDialog(
    onDismiss: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    content: @Composable (FocusRequester) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Tapping the dimmed area outside the card cancels.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .imePadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                // Swallow taps on the card so they don't dismiss.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Add todo", style = MaterialTheme.typography.titleLarge)
                content(focus)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = onSave, enabled = canSave, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
