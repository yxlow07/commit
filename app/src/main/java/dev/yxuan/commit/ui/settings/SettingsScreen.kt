package dev.yxuan.commit.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.SettingsEntity
import dev.yxuan.commit.data.repo.SettingsRepository
import dev.yxuan.commit.ui.AppViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<SettingsEntity?> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { repo.update(change) }
    }
}

/** Recap times and toggles (F6.1, F6.2, F6.5). The account/sync section is added in M7. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    accountSection: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Daily recaps")
            RecapRow(
                title = "Morning recap",
                subtitle = "Due today, overdue, chores and today's workout",
                minutes = s.morningRecapTime,
                enabled = s.morningRecapEnabled,
                onToggle = { on -> viewModel.update { it.copy(morningRecapEnabled = on) } },
                onPickTime = { editing = MORNING },
            )
            RecapRow(
                title = "Night recap",
                subtitle = "What you did today and what's due tomorrow",
                minutes = s.nightRecapTime,
                enabled = s.nightRecapEnabled,
                onToggle = { on -> viewModel.update { it.copy(nightRecapEnabled = on) } },
                onPickTime = { editing = NIGHT },
            )
            Text(
                "Recurring chores remind you at the morning recap time on the day they're due. A recap with nothing to report is skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            accountSection()
        }
    }

    editing?.let { which ->
        val current = orDefault(settings).let { if (which == MORNING) it.morningRecapTime else it.nightRecapTime }
        TimeDialog(
            initialMinutes = current,
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                viewModel.update { if (which == MORNING) it.copy(morningRecapTime = minutes) else it.copy(nightRecapTime = minutes) }
                editing = null
            },
        )
    }
}

private const val MORNING = "morning"
private const val NIGHT = "night"

private fun orDefault(settings: SettingsEntity?) = settings ?: SettingsEntity(id = "", updatedAt = 0)

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RecapRow(
    title: String,
    subtitle: String,
    minutes: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onPickTime),
        headlineContent = { Text(title) },
        supportingContent = { Text("${formatMinutes(minutes)} · $subtitle") },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
    )
}

fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}
