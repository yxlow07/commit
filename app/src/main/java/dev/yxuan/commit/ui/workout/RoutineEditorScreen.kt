package dev.yxuan.commit.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.repo.ExerciseInput
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.commitments.CommitSwitch
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.FieldLabel
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.components.WeekdayToggles
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Routine editor (pushed): name, rest between exercises, planned weekdays, exercises (F5.1). */
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
    var confirmDelete by remember { mutableStateOf(false) }
    val c = Commit.colors

    LaunchedEffect(routine) {
        routine?.let {
            if (name == null) name = it.name
            if (rest == null) rest = it.restBetweenExercisesS.toString()
            if (planned == null) planned = it.plannedDays
        }
    }

    // Routine fields are saved when leaving the screen (back arrow or system back).
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

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PushedTopBar(onBack = saveAndBack, backLabel = "Save and go back") {
            TextAction("Delete routine", onClick = { confirmDelete = true }, muted = true)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Space.screenSide, end = Space.screenSide, top = Space.l, bottom = Space.xxxl)) {
            item {
                ScreenTitle(name?.ifBlank { null } ?: "Routine")
                Spacer(Modifier.height(Space.xxl))
                Column(verticalArrangement = Arrangement.spacedBy(Space.xl)) {
                    CommitTextField(name ?: "", { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    NumberField("Rest between exercises, seconds", rest ?: "", { rest = it })
                    PlannedDays(planned ?: 0) { planned = it }
                }
                Spacer(Modifier.height(Space.section))
                SectionHeading("Exercises")
                if (exercises.isEmpty()) Text("No exercises yet.", style = Commit.type.body, color = c.textStrong, modifier = Modifier.padding(vertical = Space.s))
            }
            itemsIndexed(exercises, key = { _, e -> e.id }) { index, ex ->
                Row(Modifier.fillMaxWidth().heightIn(min = Space.rowApp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { editing = ex }.padding(vertical = 6.dp)) {
                        Text(ex.name, style = Commit.type.body, color = c.ink)
                        Text(describe(ex), style = Commit.type.meta, color = c.muted)
                    }
                    if (index > 0) TextAction("Up", onClick = { viewModel.moveExercise(ex.id, -1) }, muted = true)
                    if (index < exercises.lastIndex) TextAction("Down", onClick = { viewModel.moveExercise(ex.id, 1) }, muted = true)
                }
            }
            item {
                TextAction("Add exercise", onClick = { adding = true }, modifier = Modifier.padding(top = Space.s).offset(x = (-12).dp))
            }
        }
    }

    if (adding) {
        ExerciseDialog(null, onDismiss = { adding = false }, onDelete = null) { input ->
            viewModel.saveExercise(null, input)
            adding = false
        }
    }
    editing?.let { ex ->
        ExerciseDialog(ex, onDismiss = { editing = null }, onDelete = { viewModel.deleteExercise(ex.id); editing = null }) { input ->
            viewModel.saveExercise(ex.id, input)
            editing = null
        }
    }
    if (confirmDelete) {
        CommitDialog(
            title = "Delete ${routine?.name ?: "routine"}?",
            onDismiss = { confirmDelete = false },
            actions = {
                TextAction("Cancel", onClick = { confirmDelete = false })
                PrimaryButton("Delete routine", onClick = { viewModel.deleteRoutine(); confirmDelete = false; onBack() })
            },
        ) {
            Text("Past sessions stay in History.", style = Commit.type.body, color = c.textStrong)
        }
    }
}

/** The days this routine is planned, shown on Today and in the morning recap. */
@Composable
private fun PlannedDays(mask: Int, onChange: (Int) -> Unit) {
    Column {
        FieldLabel("Planned days")
        WeekdayToggles(mask, onChange)
    }
}

/** "3 × 8 at 60 kg, rest 90 s" or "2 × 30 s, rest 60 s". */
private fun describe(ex: ExerciseEntity): String {
    val work = if ((ex.durationSeconds ?: 0) > 0) "${ex.sets} × ${ex.durationSeconds} s"
    else "${ex.sets} × ${ex.reps} at ${WorkoutFormat.weight(ex.weight)}"
    return "$work, rest ${ex.restSeconds} s"
}

@Composable
private fun ExerciseDialog(initial: ExerciseEntity?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (ExerciseInput) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var sets by rememberSaveable { mutableStateOf((initial?.sets ?: 3).toString()) }
    var reps by rememberSaveable { mutableStateOf((initial?.reps ?: 8).toString()) }
    var weight by rememberSaveable { mutableStateOf(initial?.weight?.let { WorkoutFormat.weightValue(it) } ?: "0") }
    var rest by rememberSaveable { mutableStateOf((initial?.restSeconds ?: 90).toString()) }
    var timed by rememberSaveable { mutableStateOf((initial?.durationSeconds ?: 0) > 0) }
    var duration by rememberSaveable { mutableStateOf((initial?.durationSeconds ?: 30).toString()) }
    val valid = name.isNotBlank() && (sets.toIntOrNull() ?: 0) > 0 && (!timed || (duration.toIntOrNull() ?: 0) > 0)

    CommitDialog(
        title = if (initial == null) "New exercise" else "Edit exercise",
        onDismiss = onDismiss,
        actions = {
            if (onDelete != null) TextAction("Delete", onClick = onDelete, muted = true)
            Spacer(Modifier.weight(1f))
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton("Save", enabled = valid, onClick = {
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
            })
        },
    ) {
        CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Timed, like a plank", style = Commit.type.body, color = Commit.colors.ink, modifier = Modifier.weight(1f))
            CommitSwitch(timed, { timed = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Sets", sets, { sets = it }, Modifier.weight(1f))
            if (timed) NumberField("Seconds", duration, { duration = it }, Modifier.weight(1f))
            else NumberField("Reps", reps, { reps = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!timed) NumberField("Weight, kg", weight, { weight = it }, Modifier.weight(1f), decimal = true)
            NumberField("Rest, seconds", rest, { rest = it }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false) {
    CommitTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
        centered = true,
    )
}
