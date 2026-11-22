package dev.yxuan.commit.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.ui.components.DialogPanel
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.CommitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The widget's "More" tab (spec: Glance has no sideways scrolling, so beyond five commitments the
 * last slot opens this small picker). Picking one selects it on the widget that opened the picker.
 */
class WidgetTabPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetIdParam.name, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = appContainer
        val manager = GlanceAppWidgetManager(this)
        val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull() ?: run { finish(); return }
        val selected = MutableStateFlow<String?>(null)
        lifecycleScope.launch {
            selected.value = getAppWidgetState(this@WidgetTabPickerActivity, PreferencesGlanceStateDefinition, glanceId)[SelectedCommitmentKey]
        }
        val tabsFlow = container.commitmentRepository.observeWidgetTabs()
        val themeFlow = container.themeRepository.observeActive()

        setContent {
            val theme by themeFlow.collectAsState(ActiveTheme.Default)
            CommitTheme(active = theme) {
                val tabs by tabsFlow.collectAsState(emptyList())
                val current by selected.collectAsState()
                val c = Commit.colors
                Box(
                    Modifier.fillMaxSize().background(c.scrim)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::finish)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DialogPanel(
                        title = "Commitments",
                        actions = { TextAction("Cancel", onClick = ::finish) },
                        modifier = Modifier.widthIn(max = 420.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    ) {
                        Column(Modifier.selectableGroup()) {
                            tabs.forEach { tab ->
                                val on = tab.id == (current ?: tabs.firstOrNull()?.id)
                                Text(
                                    tab.name,
                                    style = Commit.type.body.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                                    color = if (on) c.ink else c.textStrong,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(selected = on, role = Role.RadioButton) {
                                            lifecycleScope.launch {
                                                selectTab(this@WidgetTabPickerActivity, glanceId, tab.id)
                                                finish()
                                            }
                                        }
                                        .padding(vertical = 13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
