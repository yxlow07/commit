package dev.yxuan.commit.quickadd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import dev.yxuan.commit.widget.TodoIdParam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dialog-style popup over the home screen (F3.4): a new todo from the widget's add button and the
 * app, or editing a todo from the widget's row (prefilled, with Delete).
 * Translucent theme, so the home screen stays visible behind it. Save writes to Room and closes.
 */
class QuickAddActivity : ComponentActivity() {

    /** What the caller asked for: a new todo (optionally in a commitment) or editing one todo. */
    private data class Request(val commitmentId: String?, val todoId: String?, val seq: Long)

    /**
     * The popup is single-instance, so a second tap while one is still open arrives through
     * [onNewIntent]; each request is applied once (new: preselect; edit: load the todo).
     */
    private val requests = MutableStateFlow<Request?>(null)
    private var seq = 0L

    private fun Intent.toRequest() = Request(getStringExtra(CommitmentIdParam.name), getStringExtra(TodoIdParam.name), seq++)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requests.value = intent.toRequest()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After a rotation the fields come back from saved state; don't reload over them.
        if (savedInstanceState == null) requests.value = intent.toRequest()
        val container = appContainer
        val commitmentsFlow = container.commitmentRepository.observeActive()
            .map { list -> list.filter { it.type == CommitmentType.STANDARD } }
        val themeFlow = container.themeRepository.observeActive()

        setContent {
            val activeTheme by themeFlow.collectAsState(ActiveTheme.Default)
            CommitTheme(active = activeTheme) {
                val commitments by commitmentsFlow.collectAsState(emptyList())
                var editingId by rememberSaveable { mutableStateOf<String?>(null) }
                var title by rememberSaveable { mutableStateOf("") }
                var commitmentId by rememberSaveable { mutableStateOf<String?>(null) }
                var due by remember { mutableStateOf<Due?>(null) }
                var repeatDays by rememberSaveable { mutableStateOf<Int?>(null) }
                var recurDays by rememberSaveable { mutableStateOf<Int?>(null) }
                var lead by rememberSaveable { mutableStateOf<Int?>(null) }
                var saving by remember { mutableStateOf(false) }
                val request by requests.collectAsState()

                LaunchedEffect(request) {
                    val r = request ?: return@LaunchedEffect
                    if (r.todoId != null) {
                        // Edit: the widget's todo row opens this popup instead of the full app.
                        val todo = container.todoRepository.get(r.todoId)
                        if (todo == null || todo.deleted) { finish(); return@LaunchedEffect }
                        editingId = todo.id
                        title = todo.title
                        commitmentId = todo.commitmentId
                        due = Due.of(todo, container.clock)
                        repeatDays = todo.recurIntervalDays
                        recurDays = todo.recurDays
                        lead = todo.reminderLeadMinutes
                    } else {
                        if (editingId != null) {
                            // Was editing; this is a fresh "Add": start empty.
                            editingId = null; title = ""; due = null; repeatDays = null; recurDays = null; lead = null
                        }
                        r.commitmentId?.let { commitmentId = it }
                    }
                }

                // A new todo can't go in a workout tab (or a stale id): fall back to the first todo
                // commitment. An edited todo keeps its commitment.
                LaunchedEffect(commitments, editingId) {
                    if (editingId == null && commitments.isNotEmpty() && commitments.none { it.id == commitmentId }) {
                        commitmentId = commitments.first().id
                    }
                }

                val editing = editingId
                val canSave = title.isNotBlank() && commitmentId != null && !saving
                val save = {
                    val target = commitmentId
                    if (canSave && target != null) {
                        saving = true
                        lifecycleScope.launch {
                            if (editing == null) container.todoRepository.add(target, title, due, repeatDays)
                            else container.todoRepository.update(editing, title, target, due, repeatDays, recurDays, lead)
                            finish()
                        }
                    }
                }

                QuickAddDialog(
                    title = if (editing == null) "New todo" else "Edit todo",
                    onDismiss = ::finish,
                    canSave = canSave,
                    onSave = save,
                    // Editing doesn't pop the keyboard over the fields; a new todo starts typing.
                    autoFocus = editing == null,
                    onDelete = editing?.let { id ->
                        {
                            lifecycleScope.launch {
                                container.todoRepository.delete(id)
                                finish()
                            }
                        }
                    },
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
                        recurDays = recurDays,
                        onRecurDaysChange = { recurDays = it },
                        reminderLeadMinutes = lead,
                        onReminderLeadChange = { lead = it },
                        // A new todo gets the short form; editing shows every field the todo may use.
                        compact = editing == null,
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
    title: String,
    onDismiss: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    autoFocus: Boolean,
    onDelete: (() -> Unit)?,
    content: @Composable (FocusRequester) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

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
            title = title,
            actions = {
                if (onDelete != null) {
                    TextAction("Delete todo", onClick = onDelete, muted = true)
                    Spacer(Modifier.weight(1f))
                }
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
