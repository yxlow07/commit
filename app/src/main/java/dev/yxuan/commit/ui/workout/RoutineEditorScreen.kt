package dev.yxuan.commit.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.repo.ExerciseInput
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.format.WorkoutFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Routine editor (F5.1): name, rest between exercises, and the ordered exercise list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    onBack: () -> Unit,
    viewModel: RoutineEditorViewModel = viewModel(factory = AppViewModelFactory),
) {
    val routine by viewModel.routine.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var rest by rememberSaveable { mutableStateOf<String?>(null) }
    var planned by rememberSaveable { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    // Fill the fields once the routine loads.
    LaunchedEffect(routine) {
        routine?.let {
            if (name == null) name = it.name
            if (rest == null) rest = it.restBetweenExercisesS.toString()
            if (planned == null) planned = it.plannedDays
        }
    }

    // Routine fields are saved when leaving the screen (back arrow or system back via onBack).
    val saveAndBack = {
        val r = routine
        val n = name
        if (r != null && n != null && n.isNotBlank()) {
            val restS = rest?.toIntOrNull() ?: r.restBetweenExercisesS
            val days = planned ?: r.plannedDays
            if (n != r.name || restS != r.restBetweenExercisesS || days != r.plannedDays) viewModel.saveRoutine(n, restS, days)
        }
        onBack()
    }

    BackHandler(onBack = saveAndBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routine") },
                navigationIcon = {
                    IconButton(onClick = saveAndBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Save and go back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Exercise") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name ?: "",
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField("Rest between exercises (s)", rest ?: "", { rest = it }, Modifier.fillMaxWidth())
                PlannedDays(planned ?: 0) { planned = it }
            }
            HorizontalDivider()
            if (exercises.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No exercises yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(exercises, key = { _, e -> e.id }) { index, ex ->
                        var menu by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier.clickable { editing = ex },
                            headlineContent = { Text(ex.name) },
                            supportingContent = { Text(describe(ex)) },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options for ${ex.name}")
                                    }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; editing = ex })
                                        DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { menu = false; viewModel.moveExercise(ex.id, -1) })
                                        DropdownMenuItem(text = { Text("Move down") }, enabled = index < exercises.lastIndex, onClick = { menu = false; viewModel.moveExercise(ex.id, 1) })
                                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; viewModel.deleteExercise(ex.id) })
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        ExerciseDialog(null, onDismiss = { adding = false }) { input ->
            viewModel.saveExercise(null, input)
            adding = false
        }
    }
    editing?.let { ex ->
        ExerciseDialog(ex, onDismiss = { editing = null }) { input ->
            viewModel.saveExercise(ex.id, input)
            editing = null
        }
    }
}

/** Weekday chips: the days this routine is planned, shown in the morning recap. */
@Composable
private fun PlannedDays(mask: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Planned days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = Weekdays.contains(mask, day),
                    onClick = { onChange(Weekdays.toggle(mask, day)) },
                    label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "3 × 8 @ 60 kg · rest 90 s" or "2 × 30 s · rest 60 s". */
private fun describe(ex: ExerciseEntity): String {
    val work = if ((ex.durationSeconds ?: 0) > 0) "${ex.sets} × ${ex.durationSeconds} s"
    else "${ex.sets} × ${ex.reps} @ ${WorkoutFormat.weight(ex.weight)}"
    return "$work · rest ${ex.restSeconds} s"
}

@Composable
private fun ExerciseDialog(initial: ExerciseEntity?, onDismiss: () -> Unit, onSave: (ExerciseInput) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var sets by rememberSaveable { mutableStateOf((initial?.sets ?: 3).toString()) }
    var reps by rememberSaveable { mutableStateOf((initial?.reps ?: 8).toString()) }
    var weight by rememberSaveable { mutableStateOf(initial?.weight?.let { WorkoutFormat.weight(it).removeSuffix(" kg") } ?: "0") }
    var rest by rememberSaveable { mutableStateOf((initial?.restSeconds ?: 90).toString()) }
    var timed by rememberSaveable { mutableStateOf((initial?.durationSeconds ?: 0) > 0) }
    var duration by rememberSaveable { mutableStateOf((initial?.durationSeconds ?: 30).toString()) }

    val valid = name.isNotBlank() && (sets.toIntOrNull() ?: 0) > 0 && (!timed || (duration.toIntOrNull() ?: 0) > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New exercise" else "Edit exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Timed (e.g. plank)", Modifier.weight(1f))
                    Switch(checked = timed, onCheckedChange = { timed = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
                    if (timed) {
                        NumberField("Seconds", duration, { duration = it }, Modifier.weight(1f))
                    } else {
                        NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!timed) NumberField("Weight (kg)", weight, { weight = it }, Modifier.weight(1f), decimal = true)
                    NumberField("Rest (s)", rest, { rest = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    ExerciseInput(
                        name = name,
                        sets = sets.toIntOrNull() ?: 1,
                        reps = if (timed) 0 else reps.toIntOrNull() ?: 0,
                        weight = if (timed) 0.0 else weight.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        restSeconds = rest.toIntOrNull() ?: 0,
                        durationSeconds = if (timed) duration.toIntOrNull() else null,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
