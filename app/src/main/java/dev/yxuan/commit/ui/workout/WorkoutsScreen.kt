package dev.yxuan.commit.ui.workout

import dev.yxuan.commit.data.repo.LiveWorkout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.commitments.CommitmentDialog
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.GlyphIcon
import dev.yxuan.commit.ui.components.IconAction
import dev.yxuan.commit.ui.components.NavRow
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.SectionHeader
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import java.time.LocalDate

/**
 * Workouts live on Today (F5.1, F5.2): a workout in progress at the top, then one section per
 * workout commitment with its routines and Start. History and progress live under History › Workouts.
 *
 * [rememberWorkoutSections] reads the data and owns the dialogs (call it in the screen's
 * composition); [workoutInProgress] and [workoutSections] add the rows to the screen's list.
 */
@Stable
class WorkoutSections internal constructor(
    val owners: List<CommitmentEntity>,
    val routines: List<RoutineSummary>,
    val live: LiveWorkout?,
    internal val viewModel: WorkoutsViewModel,
    internal val onEdit: (String) -> Unit,
    internal val onNewRoutine: (String) -> Unit,
)

@Composable
fun rememberWorkoutSections(
    onOpenRoutine: (String) -> Unit,
    viewModel: WorkoutsViewModel = viewModel(factory = AppViewModelFactory),
): WorkoutSections {
    val owners by viewModel.workoutCommitments.collectAsStateWithLifecycle()
    val routines by viewModel.list.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<String?>(null) }
    var namingFor by remember { mutableStateOf<String?>(null) }

    owners.orEmpty().firstOrNull { it.id == editingId }?.let { owner ->
        CommitmentDialog(
            initial = owner,
            onDismiss = { editingId = null },
            onSave = { name, _, color, show -> viewModel.updateCommitment(owner.id, name, color, show); editingId = null },
            onArchive = { viewModel.archiveCommitment(owner.id); editingId = null },
        )
    }
    namingFor?.let { ownerId ->
        var name by remember { mutableStateOf("") }
        CommitDialog(
            title = "New routine",
            onDismiss = { namingFor = null },
            actions = {
                TextAction("Cancel", onClick = { namingFor = null })
                PrimaryButton("Add routine", enabled = name.isNotBlank(), onClick = {
                    viewModel.create(ownerId, name.trim()) { onOpenRoutine(it) }
                    namingFor = null
                })
            },
        ) {
            CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        }
    }
    return WorkoutSections(owners.orEmpty(), routines, live, viewModel, { editingId = it }, { namingFor = it })
}

/** The workout in progress, if any: one row that reopens it. */
fun LazyListScope.workoutInProgress(w: WorkoutSections, onOpenLive: () -> Unit) {
    val l = w.live ?: return
    item(key = "workout-live") {
        Column(Modifier.padding(bottom = Space.xxxl)) {
            SectionHeading("In progress")
            val ex = l.exercise
            val detail = when {
                l.state.phase == WorkoutPhase.COMPLETE -> "Workout done"
                ex != null -> "${ex.name}, set ${l.state.setNo} of ${ex.sets}"
                else -> ""
            }
            NavRow(title = l.routine.name, detail = detail, onClick = onOpenLive, titleStyle = Commit.type.titleDialog.copy(fontWeight = FontWeight.Medium), minHeight = 64.dp)
        }
    }
}

/**
 * One section per workout commitment: its name as the heading with a pencil (edit the commitment)
 * and + (new routine), then its routines in Today's row style, Start in the commitment's colour.
 */
fun LazyListScope.workoutSections(w: WorkoutSections, onOpenRoutine: (String) -> Unit, onOpenLive: () -> Unit, topGap: Boolean) {
    val today = LocalDate.now()
    w.owners.forEachIndexed { i, owner ->
        val accent = Color(owner.color)
        val own = w.routines.filter { it.routine.commitmentId == owner.id }
        item(key = "wh-${owner.id}") {
            SectionHeader(owner.name, modifier = Modifier.padding(top = if (i == 0 && !topGap) 0.dp else Space.xxl)) {
                IconAction(Glyph.Pencil, "Edit ${owner.name}", { w.onEdit(owner.id) }, tint = Commit.colors.muted, glyphSize = 18.dp)
                IconAction(Glyph.Plus, "New routine in ${owner.name}", { w.onNewRoutine(owner.id) }, tint = Commit.colors.muted, glyphSize = 18.dp, modifier = Modifier.offset(x = 13.dp))
            }
        }
        if (own.isEmpty()) {
            item(key = "we-${owner.id}") {
                Text("No routines yet. Add each workout day with +, like Push, Pull and Legs.", style = Commit.type.body, color = Commit.colors.textStrong, modifier = Modifier.padding(vertical = Space.s))
            }
        }
        items(own, key = { it.routine.id }) { r ->
            val c = Commit.colors
            val planned = Weekdays.isPlanned(r.routine.plannedDays, today)
            val parts = listOfNotNull(
                if (r.exerciseCount == 1) "1 exercise" else "${r.exerciseCount} exercises",
                Weekdays.label(r.routine.plannedDays).ifEmpty { null },
                if (planned) "planned today" else null,
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = Space.xs).clickable { onOpenRoutine(r.routine.id) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.routine.name, style = Commit.type.titleDialog.copy(fontWeight = FontWeight.Medium), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(parts.joinToString(", "), style = Commit.type.meta, color = lerp(c.muted, accent, 0.35f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (w.live == null && r.exerciseCount > 0) TextAction("Start", onClick = { w.viewModel.start(r.routine.id, onOpenLive) }, color = accent)
                GlyphIcon(Glyph.Chevron, c.faint, size = 16.dp, stroke = 2.dp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
