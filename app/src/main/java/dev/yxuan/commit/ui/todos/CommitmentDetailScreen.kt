package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.DeviceClock
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.domain.TodoItem
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.format.DueFormatter
import dev.yxuan.commit.ui.format.DueStatus
import dev.yxuan.commit.ui.workout.RoutinesScreen

/** Todos of one commitment: open ones in widget order, done ones collapsed below (F2.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentDetailScreen(
    onBack: () -> Unit,
    onEditTodo: (String) -> Unit,
    onAddTodo: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onOpenLive: () -> Unit,
    viewModel: CommitmentDetailViewModel = viewModel(factory = AppViewModelFactory),
) {
    val commitment by viewModel.commitment.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    var showDone by rememberSaveable { mutableStateOf(false) }
    val isWorkout = commitment?.type == CommitmentType.WORKOUT

    // Workout commitments hold routines instead of todos (F1.2, F5.1).
    if (isWorkout) {
        RoutinesScreen(commitment?.name ?: "", onBack, onOpenRoutine, onOpenLive)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(commitment?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isWorkout) {
                FloatingActionButton(onClick = { onAddTodo(viewModel.commitmentId) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add todo")
                }
            }
        },
    ) { padding ->
        if (isWorkout) {
            Placeholder("Workout mode arrives in a later update.", Modifier.padding(padding))
            return@Scaffold
        }
        if (open.isEmpty() && done.isEmpty()) {
            Placeholder("No todos yet. Tap + to add one.", Modifier.padding(padding))
            return@Scaffold
        }
        // Recurring "done" is calculated from the clock (F4.4); done chores sit with completed todos.
        val items = open.map { TodoItem.of(it, DeviceClock) }
        val openItems = items.filterNot { it.isDone }.sortedWith(TodoItem.ORDER)
        val doneItems = items.filter { it.isDone } + done.map { TodoItem.of(it, DeviceClock) }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(openItems, key = { it.todo.id }) { item ->
                TodoRow(item, onToggle = { viewModel.setDone(item.todo.id, it) }, onClick = { onEditTodo(item.todo.id) })
            }
            if (doneItems.isNotEmpty()) {
                item(key = "done-header") {
                    ListItem(
                        modifier = Modifier.clickable { showDone = !showDone },
                        headlineContent = { Text("Done (${doneItems.size})", style = MaterialTheme.typography.titleSmall) },
                        trailingContent = {
                            Icon(
                                if (showDone) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showDone) "Hide done" else "Show done",
                            )
                        },
                    )
                }
                if (showDone) {
                    items(doneItems, key = { it.todo.id }) { item ->
                        TodoRow(item, onToggle = { viewModel.setDone(item.todo.id, it) }, onClick = { onEditTodo(item.todo.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val todo = item.todo
    val due = DueFormatter.format(item.dueAt, item.hasTime, DeviceClock)
    // Done chores show when they come back; open ones show their due label. Both note the interval.
    val supporting = buildList {
        if (item.isDone && item.isRecurring) {
            val next = DueFormatter.format(item.dueAt, false, DeviceClock).text
            add("Next: $next")
        } else if (!item.isDone && due.status != DueStatus.NONE) {
            add(due.text)
        }
        if (item.isRecurring) add("every ${todo.recurIntervalDays}d")
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Checkbox(checked = item.isDone, onCheckedChange = onToggle) },
        headlineContent = {
            Text(
                todo.title,
                fontWeight = if (!item.isDone && due.status == DueStatus.TODAY) FontWeight.Bold else null,
                textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = if (supporting.isNotEmpty()) {
            {
                Text(
                    supporting,
                    color = if (!item.isDone && due.status == DueStatus.OVERDUE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else null,
    )
}

@Composable
private fun Placeholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
