package dev.yxuan.commit.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.format.WorkoutFormat

/** Workout commitment screen: its routines, with Start (F5.1, F5.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    title: String,
    onBack: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onOpenLive: () -> Unit,
    viewModel: RoutinesViewModel = viewModel(factory = AppViewModelFactory),
) {
    val routines by viewModel.list.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Routine") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            live?.let { l ->
                Card(
                    onClick = onOpenLive,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Workout in progress", style = MaterialTheme.typography.titleSmall)
                            Text("${l.routine.name} · ${WorkoutFormat.setProgress(l)}", style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = onOpenLive) { Text("Resume") }
                    }
                }
            }
            val list = routines
            if (list != null && list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Add a routine like Push Day, then add its exercises.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list.orEmpty(), key = { _, r -> r.routine.id }) { index, summary ->
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier.clickable { onOpenRoutine(summary.routine.id) },
                            headlineContent = { Text(summary.routine.name) },
                            supportingContent = {
                                val count = if (summary.exerciseCount == 1) "1 exercise" else "${summary.exerciseCount} exercises"
                                val days = Weekdays.label(summary.routine.plannedDays)
                                Text(if (days.isEmpty()) count else "$count · $days")
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        enabled = summary.exerciseCount > 0,
                                        onClick = { viewModel.start(summary.routine.id, onOpenLive) },
                                    ) { Text("Start") }
                                    Box {
                                        IconButton(onClick = { menu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More options for ${summary.routine.name}")
                                        }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { menu = false; viewModel.move(summary.routine.id, -1) })
                                            DropdownMenuItem(text = { Text("Move down") }, enabled = index < list.orEmpty().lastIndex, onClick = { menu = false; viewModel.move(summary.routine.id, 1) })
                                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; viewModel.delete(summary.routine.id) })
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New routine") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Push Day") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    creating = false
                    viewModel.create(name, onOpenRoutine)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}
