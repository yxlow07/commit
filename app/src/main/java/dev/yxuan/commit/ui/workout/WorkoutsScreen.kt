package dev.yxuan.commit.ui.workout

import dev.yxuan.commit.ui.components.SegmentedControl
import dev.yxuan.commit.ui.components.GlyphIcon
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.SectionHeader
import dev.yxuan.commit.ui.components.PrimaryRoundButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
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

    // Laid out like Today: title with the round + (new routine), a one-line summary, then each
    // workout commitment as a captioned group (pencil to edit it) of rows in Today's row style.
    val plannedToday = routines.filter { Weekdays.isPlanned(it.routine.plannedDays, today) }.map { it.routine.name }
    val summary = when {
        live != null -> "${live!!.routine.name} in progress."
        plannedToday.isNotEmpty() -> plannedToday.joinToString(", ") + " planned today."
        routines.isNotEmpty() -> "Nothing planned today."
        else -> null
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = ScreenPadding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle("Workouts", Modifier.weight(1f))
                    if (!owners.isNullOrEmpty()) PrimaryRoundButton(onClick = { naming = true }, contentDescription = "New routine")
                }
                if (summary != null) Text(summary, style = Commit.type.bodyL, color = c.textStrong, modifier = Modifier.widthIn(max = 300.dp))
            }
            Spacer(Modifier.height(Space.xxxl))
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
                NavRow(title = l.routine.name, detail = detail, onClick = onOpenLive, titleStyle = Commit.type.titleDialog.copy(fontWeight = FontWeight.Medium), minHeight = 64.dp)
                Spacer(Modifier.height(Space.xxl))
            }
        }
        if (owners != null && owners!!.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                    Text("Add a workout commitment to plan routines.", style = Commit.type.bodyL, color = c.textStrong)
                    PrimaryButton("New workout commitment", onClick = { newCommitment = true })
                }
            }
        }
        owners.orEmpty().forEachIndexed { i, owner ->
            val accent = Color(owner.color)
            val own = routines.filter { it.routine.commitmentId == owner.id }
            item(key = "h-${owner.id}") {
                SectionHeader(owner.name, Glyph.Pencil, "Edit ${owner.name}", onAction = { editingId = owner.id }, modifier = Modifier.padding(top = if (i == 0) 0.dp else Space.xxl))
            }
            if (own.isEmpty()) {
                item(key = "e-${owner.id}") {
                    Text("No routines yet. Add one with +.", style = Commit.type.body, color = c.textStrong, modifier = Modifier.padding(vertical = Space.s))
                }
            }
            items(own, key = { it.routine.id }) { r ->
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
                    // Start in the commitment's colour, like Today's counts.
                    if (live == null && r.exerciseCount > 0) TextAction("Start", onClick = { viewModel.start(r.routine.id, onOpenLive) }, color = accent)
                    GlyphIcon(Glyph.Chevron, c.faint, size = 16.dp, stroke = 2.dp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    owners.orEmpty().firstOrNull { it.id == editingId }?.let { owner ->
        CommitmentDialog(
            initial = owner,
            onDismiss = { editingId = null },
            onSave = { name, _, color, show -> viewModel.updateCommitment(owner.id, name, color, show); editingId = null },
            onArchive = { viewModel.archiveCommitment(owner.id); editingId = null },
        )
    }
    if (naming) {
        var name by remember { mutableStateOf("") }
        var ownerId by remember { mutableStateOf(owners?.firstOrNull()?.id) }
        CommitDialog(
            title = "New routine",
            onDismiss = { naming = false },
            actions = {
                TextAction("Cancel", onClick = { naming = false })
                PrimaryButton("Add routine", enabled = name.isNotBlank(), onClick = {
                    val owner = owners?.firstOrNull { it.id == ownerId } ?: owners?.firstOrNull() ?: return@PrimaryButton
                    viewModel.create(owner.id, name.trim()) { onOpenRoutine(it) }
                    naming = false
                })
            },
        ) {
            CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            val list = owners.orEmpty()
            if (list.size > 1) {
                SegmentedControl(list.map { it.id }, ownerId, { ownerId = it }, label = { id -> list.first { it.id == id }.name }, scrollable = true)
            }
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
