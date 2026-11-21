package dev.yxuan.commit.ui.commitments

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.notify.Notifications
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.common.CommitmentPalette
import dev.yxuan.commit.ui.todos.ColorDot

/** Start screen: the list of commitments (F1.1, F1.2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentsScreen(
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: CommitmentsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val commitments by viewModel.commitments.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CommitmentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Commit") },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, contentDescription = "History") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Commitment") },
            )
        },
    ) { padding ->
        val list = commitments
        Column(Modifier.padding(padding)) {
            NotificationsBanner()
            if (list != null && list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Add a commitment like Study or Work to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list.orEmpty(), key = { _, c -> c.id }) { index, c ->
                        CommitmentRow(
                            commitment = c,
                            canMoveUp = index > 0,
                            canMoveDown = index < list.orEmpty().lastIndex,
                            onClick = { onOpen(c.id) },
                            onEdit = { editing = c },
                            onMove = { delta -> viewModel.move(c.id, delta) },
                            onArchive = { viewModel.archive(c.id) },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CommitmentDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, type, color, _ ->
                viewModel.create(name, type, color)
                creating = false
            },
        )
    }
    editing?.let { c ->
        CommitmentDialog(
            initial = c,
            onDismiss = { editing = null },
            onSave = { name, _, color, showInWidget ->
                viewModel.update(c.id, name, color, showInWidget)
                editing = null
            },
        )
    }
}

@Composable
private fun CommitmentRow(
    commitment: CommitmentEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onArchive: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { ColorDot(commitment.color, Modifier.size(16.dp)) },
        headlineContent = { Text(commitment.name) },
        supportingContent = {
            val parts = buildList {
                if (commitment.type == CommitmentType.WORKOUT) add("Workout")
                if (!commitment.showInWidget) add("Hidden from widget")
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options for ${commitment.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = canMoveUp,
                        onClick = { menu = false; onMove(-1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = canMoveDown,
                        onClick = { menu = false; onMove(1) },
                    )
                    DropdownMenuItem(text = { Text("Archive") }, onClick = { menu = false; onArchive() })
                }
            }
        },
    )
}

/** Shown while notifications are off: reminders, chores and timers can't alert (F7). */
@Composable
private fun NotificationsBanner() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = Notifications.canPost(context)
        onPauseOrDispose {}
    }
    if (allowed) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Notifications are off, so reminders and timers can't alert you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }) { Text("Turn on") }
        }
    }
}

/** Create (initial = null) or edit a commitment: name, colour, type on create, widget visibility on edit. */
@Composable
private fun CommitmentDialog(
    initial: CommitmentEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, type: CommitmentType, color: Int, showInWidget: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: CommitmentPalette.first()) }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: CommitmentType.STANDARD) }
    var showInWidget by rememberSaveable { mutableStateOf(initial?.showInWidget ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New commitment" else "Edit commitment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommitmentPalette.forEach { c ->
                        ColorSwatch(c, selected = c == color, onClick = { color = c })
                    }
                }
                if (initial == null) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CommitmentType.entries.forEachIndexed { index, t ->
                            SegmentedButton(
                                selected = type == t,
                                onClick = { type = t },
                                shape = SegmentedButtonDefaults.itemShape(index, CommitmentType.entries.size),
                            ) { Text(if (t == CommitmentType.STANDARD) "Todos" else "Workout") }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show on widget", Modifier.weight(1f))
                        Switch(checked = showInWidget, onCheckedChange = { showInWidget = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, type, color, showInWidget) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(Color(color), CircleShape)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White)
    }
}
