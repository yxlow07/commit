package dev.yxuan.commit.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.commitments.CommitmentDialog
import dev.yxuan.commit.ui.common.CommitmentPalette
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.NavRow
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.ScreenPadding
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import java.time.LocalDate

/**
 * Workouts (nav): the live workout if one is running, then every routine with Start (F5.1, F5.2).
 * History and progress live under History › Workouts.
 */
@Composable
fun WorkoutsScreen(
    onOpenRoutine: (String) -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val owners by viewModel.workoutCommitments.collectAsStateWithLifecycle()
    val routines by viewModel.list.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val c = Commit.colors
    var naming by remember { mutableStateOf(false) }
    var newCommitment by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()

    LazyColumn(modifier.fillMaxSize(), contentPadding = ScreenPadding) {
        item {
            ScreenTitle("Workouts")
            Spacer(Modifier.height(Space.xxl))
        }
        live?.let { l ->
            item {
                SectionHeading("In progress")
                val ex = l.exercise
                val detail = when {
                    l.state.phase == WorkoutPhase.COMPLETE -> "Workout done"
                    ex != null -> "${ex.name}, set ${l.state.setNo} of ${ex.sets}"
                    else -> ""
                }
                NavRow(title = l.routine.name, detail = detail, onClick = onOpenLive)
                Spacer(Modifier.height(Space.section))
            }
        }
        if (owners != null && owners!!.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                    Text("Add a workout commitment to plan routines.", style = Commit.type.bodyL, color = c.textStrong)
                    PrimaryButton("New workout commitment", onClick = { newCommitment = true })
                }
            }
        } else if (owners != null) {
            if (routines.isNotEmpty()) item { SectionHeading("Routines") }
            items(routines, key = { it.routine.id }) { r ->
                val planned = Weekdays.isPlanned(r.routine.plannedDays, today)
                val parts = listOfNotNull(
                    if (r.exerciseCount == 1) "1 exercise" else "${r.exerciseCount} exercises",
                    Weekdays.label(r.routine.plannedDays).ifEmpty { null },
                    if (planned) "planned today" else null,
                )
                Row(Modifier.fillMaxWidth().heightIn(min = Space.rowApp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onOpenRoutine(r.routine.id) }.padding(vertical = 6.dp)) {
                        Text(r.routine.name, style = Commit.type.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(parts.joinToString(", "), style = Commit.type.meta, color = c.muted)
                    }
                    if (live == null && r.exerciseCount > 0) {
                        TextAction("Start", onClick = { viewModel.start(r.routine.id, onOpenLive) })
                    }
                }
            }
            item {
                Column(Modifier.padding(top = Space.s).offset(x = (-12).dp)) {
                    TextAction("New routine", onClick = { naming = true }, muted = true)
                    owners.orEmpty().forEach { owner ->
                        TextAction("Edit ${owner.name}", onClick = { editingId = owner.id }, muted = true)
                    }
                }
            }
        }
    }

    owners.orEmpty().firstOrNull { it.id == editingId }?.let { owner ->
        CommitmentDialog(
            initial = owner,
            onDismiss = { editingId = null },
            onSave = { name, _, color, show -> viewModel.updateCommitment(owner.id, name, color, show); editingId = null },
            onMove = { viewModel.moveCommitment(owner.id, it) },
            onArchive = { viewModel.archiveCommitment(owner.id); editingId = null },
        )
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        CommitDialog(
            title = "New routine",
            onDismiss = { naming = false },
            actions = {
                TextAction("Cancel", onClick = { naming = false })
                PrimaryButton("Add routine", enabled = name.isNotBlank(), onClick = {
                    val owner = owners?.firstOrNull() ?: return@PrimaryButton
                    viewModel.create(owner.id, name.trim()) { onOpenRoutine(it) }
                    naming = false
                })
            },
        ) {
            CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        }
    }
    if (newCommitment) {
        var name by remember { mutableStateOf("Gym") }
        CommitDialog(
            title = "New workout commitment",
            onDismiss = { newCommitment = false },
            actions = {
                TextAction("Cancel", onClick = { newCommitment = false })
                PrimaryButton("Add commitment", enabled = name.isNotBlank(), onClick = {
                    viewModel.createCommitment(name.trim(), CommitmentPalette.first())
                    newCommitment = false
                })
            },
        ) {
            CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        }
    }
}
