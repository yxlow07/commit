package dev.yxuan.commit.ui.commitments

import androidx.compose.foundation.layout.Spacer
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.IconTileButton
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.ui.common.CommitmentPalette
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.FieldLabel
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.SegmentedControl
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit

/** Switch in the greyscale tokens. */
@Composable
fun CommitSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Commit.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.ink, checkedThumbColor = c.onInk, checkedBorderColor = c.ink,
            uncheckedTrackColor = c.fill, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.track,
        ),
    )
}

/**
 * Create (initial = null) or edit a commitment (F1.1–F1.3): name, type on create; on edit also
 * "Show on widget" and archive. Reordering is on Today: hold a commitment and drag it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommitmentDialog(
    initial: CommitmentEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: CommitmentType, color: Int, showInWidget: Boolean) -> Unit,
    onArchive: (() -> Unit)? = null,
    /** The type a new commitment starts as (e.g. Collection from the Collections section's +). */
    initialType: CommitmentType = CommitmentType.STANDARD,
) {
    val c = Commit.colors
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: CommitmentPalette.first()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: initialType) }
    var showInWidget by rememberSaveable { mutableStateOf(initial?.showInWidget ?: true) }

    CommitDialog(
        title = when (initial?.type ?: type) {
            CommitmentType.COLLECTION -> if (initial == null) "New collection" else "Edit collection"
            CommitmentType.WORKOUT -> if (initial == null) "New workout" else "Edit workout"
            CommitmentType.STANDARD -> if (initial == null) "New commitment" else "Edit commitment"
        },
        onDismiss = onDismiss,
        actions = {
            // Archive bottom left, like the trash in the todo and exercise editors.
            onArchive?.let {
                IconTileButton(Glyph.Archive, onClick = it, contentDescription = "Archive ${initial?.name ?: "commitment"}")
                Spacer(Modifier.weight(1f))
            }
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton(
                if (initial != null) "Save" else when (type) {
                    CommitmentType.COLLECTION -> "Add collection"
                    CommitmentType.WORKOUT -> "Add workout"
                    CommitmentType.STANDARD -> "Add commitment"
                },
                onClick = { onSave(name, type, color, showInWidget) },
                enabled = name.isNotBlank(),
            )
        },
    ) {
        CommitTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        if (initial == null) {
            Column {
                FieldLabel("Type")
                SegmentedControl(CommitmentType.entries, type, { type = it }, label = { when (it) { CommitmentType.STANDARD -> "Todos"; CommitmentType.WORKOUT -> "Workout"; CommitmentType.COLLECTION -> "Collection" } })
            }
        } else if (initial.type != CommitmentType.COLLECTION) {
            // Collections never show on the widget.
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Show on widget", style = Commit.type.body, color = c.ink, modifier = Modifier.weight(1f))
                CommitSwitch(showInWidget, { showInWidget = it })
            }
        }
        Column {
            FieldLabel("Colour")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CommitmentPalette.forEachIndexed { i, swatch ->
                    val on = swatch == color
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable(role = Role.RadioButton) { color = swatch }
                            .semantics { contentDescription = "Colour ${i + 1}"; selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(if (on) 28.dp else 22.dp)
                                .background(Color(swatch), CircleShape)
                                .then(if (on) Modifier.border(2.dp, c.ink, CircleShape) else Modifier)
                        )
                    }
                }
            }
        }
        if (initial != null && initial.type == CommitmentType.STANDARD) {
            Text("To reorder, drag a commitment's handle on Today.", style = Commit.type.meta, color = c.muted)
        }
    }
}
