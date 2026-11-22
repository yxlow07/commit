package dev.yxuan.commit.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.dao.SetRow
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum

/** Every set of a session as done; tap a set to fix reps or weight, or delete it (F8.5). */
@Composable
fun SessionDetailScreen(onBack: () -> Unit, viewModel: SessionDetailViewModel = viewModel(factory = AppViewModelFactory)) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SetRow?>(null) }
    val c = Commit.colors

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PushedTopBar(onBack = onBack, backLabel = "Back to History")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.screenSide, end = Space.screenSide, top = Space.l, bottom = Space.xxxl)) {
            session?.let { s ->
                item {
                    ScreenTitle(s.routineName)
                    Text(
                        "${WorkoutFormat.sessionDate(s)}, ${WorkoutFormat.sessionMeta(s)}, ${WorkoutFormat.volume(s.volume)}",
                        style = Commit.type.meta.tnum(),
                        color = c.muted,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    Spacer(Modifier.height(Space.s))
                }
            }
            sets.groupBy { it.exerciseName }.forEach { (name, rows) ->
                item(key = "h$name") { SectionHeading(name, Modifier.padding(top = Space.l)) }
                items(rows, key = { it.set.id }) { row ->
                    val timed = row.set.reps == 0 && row.set.weight == 0.0
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Space.rowWidget).clickable(enabled = !timed) { editing = row },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Set ${row.set.setNo}", style = Commit.type.body, color = c.ink, modifier = Modifier.weight(1f))
                        Text(
                            if (timed) "Timed" else "${row.set.reps} × ${WorkoutFormat.weight(row.set.weight)}",
                            style = Commit.type.body.tnum(),
                            color = c.textStrong,
                        )
                    }
                }
            }
        }
    }

    editing?.let { row ->
        var reps by rememberSaveable(row.set.id) { mutableStateOf(row.set.reps.toString()) }
        var weight by rememberSaveable(row.set.id) { mutableStateOf(WorkoutFormat.weightValue(row.set.weight)) }
        CommitDialog(
            title = "${row.exerciseName}, set ${row.set.setNo}",
            onDismiss = { editing = null },
            actions = {
                TextAction("Delete set", onClick = { viewModel.delete(row.set.id); editing = null }, muted = true)
                Spacer(Modifier.weight(1f))
                TextAction("Cancel", onClick = { editing = null })
                PrimaryButton("Save", onClick = {
                    viewModel.edit(row.set.id, reps.toIntOrNull() ?: row.set.reps, weight.replace(',', '.').toDoubleOrNull() ?: row.set.weight)
                    editing = null
                })
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommitTextField(reps, { v -> reps = v.filter(Char::isDigit).take(4) }, label = "Reps",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                CommitTextField(weight, { v -> weight = v.filter { it.isDigit() || it == '.' || it == ',' }.take(6) }, label = "Weight, kg",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
        }
    }
}
