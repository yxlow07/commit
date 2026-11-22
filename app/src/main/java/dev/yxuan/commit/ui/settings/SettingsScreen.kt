package dev.yxuan.commit.ui.settings

import kotlinx.coroutines.flow.map
import dev.yxuan.commit.data.repo.CommitmentRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.SettingsEntity
import dev.yxuan.commit.data.repo.SettingsRepository
import dev.yxuan.commit.data.repo.ThemeRepository
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.commitments.CommitSwitch
import dev.yxuan.commit.ui.common.NotificationsOffNotice
import dev.yxuan.commit.ui.components.NavRow
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TimePickerSheet
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class SettingsViewModel(
    private val repo: SettingsRepository,
    themeRepo: ThemeRepository,
    commitmentRepo: CommitmentRepository,
) : ViewModel() {
    val archivedCount: StateFlow<Int> =
        commitmentRepo.observeArchived().map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val settings: StateFlow<SettingsEntity?> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeTheme: StateFlow<ActiveTheme> =
        themeRepo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveTheme.Default)

    fun update(change: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { repo.update(change) }
    }
}

/** Settings (nav): recap times and toggles (F6.1, F6.2, F6.5), theme, account and sync. */
@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    onOpenArchived: () -> Unit,
    modifier: Modifier = Modifier,
    accountSection: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val c = Commit.colors

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Space.screenSide, end = Space.screenSide, top = Space.screenTop, bottom = Space.xxxl),
    ) {
        ScreenTitle("Settings")
        Spacer(Modifier.height(Space.xxl))
        NotificationsOffNotice(Modifier.padding(bottom = Space.l))

        val s = settings ?: return@Column
        SectionHeading("Daily recaps")
        RecapRow(
            title = "Morning recap",
            detail = "Today's plan",
            minutes = s.morningRecapTime,
            enabled = s.morningRecapEnabled,
            onToggle = { on -> viewModel.update { it.copy(morningRecapEnabled = on) } },
            onPickTime = { editing = MORNING },
        )
        RecapRow(
            title = "Night recap",
            detail = "Today's wins, tomorrow's plan",
            minutes = s.nightRecapTime,
            enabled = s.nightRecapEnabled,
            onToggle = { on -> viewModel.update { it.copy(nightRecapEnabled = on) } },
            onPickTime = { editing = NIGHT },
        )

        Spacer(Modifier.height(Space.section))
        SectionHeading("Appearance")
        NavRow(title = "Themes", trailing = activeTheme.spec.name, onClick = onOpenTheme)

        Spacer(Modifier.height(Space.section))
        SectionHeading("Commitments")
        NavRow(title = "Archived", trailing = archivedCount.takeIf { it > 0 }?.toString(), onClick = onOpenArchived)

        Spacer(Modifier.height(Space.section))
        accountSection()
    }

    editing?.let { which ->
        val current = (settings ?: SettingsEntity(id = "", updatedAt = 0)).let { if (which == MORNING) it.morningRecapTime else it.nightRecapTime }
        TimePickerSheet(
            initial = LocalTime.of(current / 60, current % 60),
            onPick = { t ->
                val minutes = t.hour * 60 + t.minute
                viewModel.update { if (which == MORNING) it.copy(morningRecapTime = minutes) else it.copy(nightRecapTime = minutes) }
            },
            onDismiss = { editing = null },
        )
    }
}

private const val MORNING = "morning"
private const val NIGHT = "night"

/** Title and detail; the time opens a picker, the switch turns the recap on or off. */
@Composable
private fun RecapRow(title: String, detail: String, minutes: Int, enabled: Boolean, onToggle: (Boolean) -> Unit, onPickTime: () -> Unit) {
    val c = Commit.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onPickTime).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = Commit.type.body, color = c.ink, modifier = Modifier.weight(1f, fill = false))
                Text("  " + formatMinutes(minutes), style = Commit.type.body.tnum(), color = if (enabled) c.ink else c.muted)
            }
            Text(detail, style = Commit.type.meta, color = c.muted)
        }
        CommitSwitch(enabled, onToggle, Modifier.padding(start = Space.m))
    }
}

fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
