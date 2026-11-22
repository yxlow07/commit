package dev.yxuan.commit.ui.workout

import dev.yxuan.commit.ui.components.Stepper
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import dev.yxuan.commit.ui.components.WheelPicker
import dev.yxuan.commit.ui.components.SegmentedControl
import dev.yxuan.commit.ui.components.DeleteIconButton
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.customActions
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.GlyphIcon
import dev.yxuan.commit.ui.components.reorderActions
import dev.yxuan.commit.ui.components.reorderDrag
import dev.yxuan.commit.ui.components.reorderLift
import dev.yxuan.commit.ui.components.ReorderHandle
import dev.yxuan.commit.ui.components.rememberReorderState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

    // Exercises reorder by dragging their grip, or holding the row (as on Today).
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState)
    reorder.sync(exercises.map { it.id })
    val exById = exercises.associateBy { it.id }
    val shownExercises = reorder.order.mapNotNull { exById[it] }
    val exerciseIds = shownExercises.map { it.id }
    val saveOrder: (List<String>) -> Unit = { viewModel.reorderExercises(it) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        PushedTopBar(onBack = saveAndBack, backLabel = "Save and go back") {
            TextAction("Delete routine", onClick = { confirmDelete = true }, muted = true)
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Space.screenSide, end = Space.screenSide, top = Space.l, bottom = Space.xxxl)) {
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
            items(shownExercises, key = { it.id }) { ex ->
                Row(
                    Modifier
                        .then(if (reorder.dragging == ex.id) Modifier else Modifier.animateItem())
                        .reorderLift(reorder, ex.id)
                        .reorderDrag(reorder, ex.id, exerciseIds, saveOrder)
                        .fillMaxWidth().heightIn(min = Space.rowApp)
                        .clickable { editing = ex }
                        .semantics { customActions = reorderActions(ex.id, exerciseIds, saveOrder) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReorderHandle(reorder, ex.id, exerciseIds, saveOrder)
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(ex.name, style = Commit.type.body, color = c.ink)
                        Text(describe(ex), style = Commit.type.meta, color = c.muted)
                    }
                    GlyphIcon(Glyph.Chevron, c.faint, size = 16.dp, stroke = 2.dp)
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

/**
 * New / edit exercise, laid out like the todo editor: name, a segmented control for the kind,
 * then the numbers as the live workout's steppers in a 2 x 2 grid (compact, one tap per step), and
 * trash / Cancel / Save exercise.
 */
@Composable
private fun ExerciseDialog(initial: ExerciseEntity?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (ExerciseInput) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var sets by rememberSaveable { mutableIntStateOf(initial?.sets ?: 3) }
    var reps by rememberSaveable { mutableIntStateOf(initial?.reps?.takeIf { it > 0 } ?: 8) }
    // Weight in tenths of a kilogram, so 2.5 kg steps stay exact.
    var weightTenths by rememberSaveable { mutableIntStateOf(((initial?.weight ?: 0.0) * 10).roundToInt()) }
    var rest by rememberSaveable { mutableIntStateOf(initial?.restSeconds ?: 90) }
    var timed by rememberSaveable { mutableStateOf((initial?.durationSeconds ?: 0) > 0) }
    var duration by rememberSaveable { mutableIntStateOf(initial?.durationSeconds?.takeIf { it > 0 } ?: 30) }
    val valid = name.isNotBlank()

    CommitDialog(
        title = if (initial == null) "New exercise" else "Edit exercise",
        onDismiss = onDismiss,
        actions = {
            if (onDelete != null) {
                DeleteIconButton(onClick = onDelete, contentDescription = "Delete exercise")
                Spacer(Modifier.weight(1f))
            }
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton("Save exercise", enabled = valid, onClick = {
                onSave(
                    ExerciseInput(
                        name = name,
                        sets = sets,
                        reps = if (timed) 0 else reps,
                        weight = if (timed) 0.0 else weightTenths / 10.0,
                        restSeconds = rest,
                        durationSeconds = if (timed) duration else null,
                    )
                )
            })
        },
    ) {
        CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        SegmentedControl(listOf(false, true), timed, { timed = it }, label = { if (it) "Timed" else "Sets and reps" })
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Stepper(sets.toString(), if (sets == 1) "set" else "sets", { sets = (sets - 1).coerceAtLeast(1) }, { sets = (sets + 1).coerceAtMost(99) }, "Fewer sets", "More sets", Modifier.weight(1f))
            if (timed) Stepper(duration.toString(), "seconds", { duration = stepDown(duration, 5, 5) }, { duration = stepUp(duration, 5, 3600) }, "Shorter", "Longer", Modifier.weight(1f))
            else Stepper(reps.toString(), "reps", { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(999) }, "Fewer reps", "More reps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            if (!timed) {
                // TODO(human): choose the weight step. Now 2.5 kg (tenths of a kg, so 25 = 2.5 kg).
                val weightStep = 25
                Stepper(WorkoutFormat.weightValue(weightTenths / 10.0), "kg", { weightTenths = stepDown(weightTenths, weightStep, 0) }, { weightTenths = stepUp(weightTenths, weightStep, 9990) }, "Less weight", "More weight", Modifier.weight(1f))
            }
            Stepper(rest.toString(), "s rest", { rest = stepDown(rest, 15, 0) }, { rest = stepUp(rest, 15, 3600) }, "Shorter rest", "Longer rest", Modifier.weight(1f))
        }
    }
}

/** The next multiple of [step] above [v] (so an older in-between value snaps onto the grid), at most [max]. */
private fun stepUp(v: Int, step: Int, max: Int) = ((v / step + 1) * step).coerceAtMost(max)

/** The next multiple of [step] below [v], at least [min]. */
private fun stepDown(v: Int, step: Int, min: Int) = (if (v % step == 0) v - step else v / step * step).coerceAtLeast(min)

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
