package dev.yxuan.commit.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.dao.SetRow
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.settings.SectionTitle

/** Every set of a session as done; tap a set to fix reps/weight or delete it (F8.5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(onBack: () -> Unit, viewModel: SessionDetailViewModel = viewModel(factory = AppViewModelFactory)) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SetRow?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.routineName ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            session?.let { s -> item { Text(sessionLine(s), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) } }
            sets.groupBy { it.exerciseName }.forEach { (name, rows) ->
                item(key = "h$name") { SectionTitle(name) }
                items(rows, key = { it.set.id }) { row ->
                    val timed = row.set.reps == 0 && row.set.weight == 0.0
                    ListItem(
                        modifier = Modifier.clickable(enabled = !timed) { editing = row },
                        headlineContent = { Text("Set ${row.set.setNo}") },
                        trailingContent = {
                            Text(if (timed) "timed" else "${row.set.reps} × ${WorkoutFormat.weight(row.set.weight)}", style = MaterialTheme.typography.bodyLarge)
                        },
                    )
                }
            }
        }
    }

    editing?.let { row ->
        var reps by rememberSaveable(row.set.id) { mutableStateOf(row.set.reps.toString()) }
        var weight by rememberSaveable(row.set.id) { mutableStateOf(WorkoutFormat.weight(row.set.weight).removeSuffix(" kg")) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${row.exerciseName} · set ${row.set.setNo}") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = reps, onValueChange = { v -> reps = v.filter(Char::isDigit).take(4) },
                        label = { Text("Reps") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = weight, onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                        label = { Text("Weight (kg)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.edit(row.set.id, reps.toIntOrNull() ?: row.set.reps, weight.replace(',', '.').toDoubleOrNull() ?: row.set.weight)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.delete(row.set.id); editing = null }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
