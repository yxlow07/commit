package dev.yxuan.commit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.domain.TodoItem
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.ThemeMode
import dev.yxuan.commit.quickadd.QuickAddActivity
import dev.yxuan.commit.ui.format.DueFormatter
import dev.yxuan.commit.ui.format.DueStatus
import dev.yxuan.commit.ui.theme.colorSchemeFor
import androidx.glance.material3.ColorProviders
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Per-widget-instance state key: the commitment whose tab is selected (phone-only, not synced). */
val SelectedCommitmentKey = stringPreferencesKey("selected_commitment_id")

val TodoIdParam = ActionParameters.Key<String>("todo_id")
val CommitmentIdParam = ActionParameters.Key<String>("commitment_id")

/**
 * Home-screen widget (F3): a tab per commitment, the selected commitment's undone todos,
 * a checkbox per row and a "+ Add" button. Reads Room only, never the network.
 */
class CommitWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Exact: the list grows with the widget's height (F3.6).
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val tabsFlow = container.commitmentRepository.observeWidgetTabs()
        val todosFlow = container.todoRepository.observeOpenForWidget()
        val routinesFlow = container.routineRepository.observeSummaries()
        val liveFlow = container.workoutRepository.observeLive()
        val themeFlow = container.themeRepository.observeActive()
        // Load once up front so the first frame already has data (no empty flash).
        val initialTabs = tabsFlow.first()
        val initialTodos = todosFlow.first()
        val initialRoutines = routinesFlow.first()
        val initialLive = liveFlow.first()
        val initialTheme = themeFlow.first()
        val clock = container.clock

        provideContent {
            val tabs by tabsFlow.collectAsState(initialTabs)
            val todos by todosFlow.collectAsState(initialTodos)
            val routines by routinesFlow.collectAsState(initialRoutines)
            val live by liveFlow.collectAsState(initialLive)
            val activeTheme by themeFlow.collectAsState(initialTheme)
            val selectedId = currentState<Preferences>()[SelectedCommitmentKey]
            // Fall back to the first tab if the stored one was archived, deleted or hidden.
            val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.firstOrNull()

            val glanceColors = when (val t = activeTheme) {
                is ActiveTheme.Dynamic -> GlanceTheme.colors
                is ActiveTheme.Fixed -> {
                    val lightScheme = colorSchemeFor(t.spec.light, isDark = false)
                    val darkScheme = colorSchemeFor(t.spec.dark, isDark = true)
                    when (t.mode) {
                        ThemeMode.SYSTEM -> ColorProviders(light = lightScheme, dark = darkScheme)
                        ThemeMode.LIGHT -> ColorProviders(light = lightScheme, dark = lightScheme)
                        ThemeMode.DARK -> ColorProviders(light = darkScheme, dark = darkScheme)
                    }
                }
            }

            GlanceTheme(colors = glanceColors) {
                WidgetContent(
                    tabs = tabs,
                    selected = selected,
                    // Recalculated on every redraw, so chores reappear after the midnight alarm (F4.4).
                    todos = TodoItem.open(todos.filter { it.commitmentId == selected?.id }, clock),
                    clock = clock,
                    routines = routines.filter { it.routine.commitmentId == selected?.id },
                    live = live,
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    tabs: List<CommitmentEntity>,
    selected: CommitmentEntity?,
    todos: List<TodoItem>,
    clock: Clock,
    routines: List<RoutineSummary>,
    live: LiveWorkout?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (selected == null) {
            EmptyMessage("Create a commitment in the app to get started", openApp = true)
            return@Column
        }
        TabRow(tabs, selected)
        Spacer(GlanceModifier.height(6.dp))
        when {
            selected.type == CommitmentType.WORKOUT -> WorkoutTab(routines, live)
            todos.isEmpty() -> EmptyMessage("All done here", openApp = false)
            else -> TodoList(todos, clock)
        }
    }
}

@Composable
private fun TabRow(tabs: List<CommitmentEntity>, selected: CommitmentEntity) {
    // Glance has no horizontally scrolling row, so show as many tabs as fit beside the add button.
    val width = LocalSize.current.width
    val maxTabs = (((width - 56.dp) / 76.dp).toInt()).coerceAtLeast(1)
    val visible = visibleTabs(tabs, selected, maxTabs)

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            visible.forEach { tab ->
                Tab(tab, isSelected = tab.id == selected.id)
                Spacer(GlanceModifier.width(4.dp))
            }
        }
        if (selected.type == CommitmentType.WORKOUT) return@Row
        Box(
            modifier = GlanceModifier
                .size(width = 48.dp, height = 32.dp)
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.primary)
                .clickable(
                    actionStartActivity<QuickAddActivity>(
                        actionParametersOf(CommitmentIdParam to selected.id)
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+ Add",
                style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** First [max] tabs, but always including the selected one. */
internal fun <T> visibleTabs(tabs: List<T>, selected: T, max: Int): List<T> {
    if (tabs.size <= max) return tabs
    val head = tabs.take(max)
    return if (selected in head) head else head.dropLast(1) + selected
}

@Composable
private fun Tab(tab: CommitmentEntity, isSelected: Boolean) {
    val background = if (isSelected) GlanceTheme.colors.secondaryContainer else GlanceTheme.colors.widgetBackground
    val textColor = if (isSelected) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurfaceVariant
    Row(
        modifier = GlanceModifier
            .height(32.dp)
            .cornerRadius(16.dp)
            .background(background)
            .padding(horizontal = 10.dp)
            .clickable(actionRunCallback<SelectTabAction>(actionParametersOf(CommitmentIdParam to tab.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(8.dp).cornerRadius(4.dp).background(ColorProvider(Color(tab.color))),
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = tab.name,
            maxLines = 1,
            style = TextStyle(
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun TodoList(todos: List<TodoItem>, clock: Clock) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(todos, itemId = { it.todo.id.hashCode().toLong() }) { item ->
            TodoRow(item, clock)
        }
    }
}

@Composable
private fun TodoRow(item: TodoItem, clock: Clock) {
    val todo = item.todo
    val due = DueFormatter.format(item.dueAt, item.hasTime, clock)
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = false,
            onCheckedChange = actionRunCallback<ToggleTodoAction>(actionParametersOf(TodoIdParam to todo.id)),
        )
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>(actionParametersOf(TodoIdParam to todo.id))),
        ) {
            Text(
                text = todo.title,
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = if (due.status == DueStatus.TODAY) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (due.status != DueStatus.NONE) {
                Text(
                    text = due.text,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (due.status == DueStatus.OVERDUE) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (due.status == DueStatus.OVERDUE) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String, openApp: Boolean) {
    val modifier = GlanceModifier.fillMaxSize().let {
        if (openApp) it.clickable(actionStartActivity<MainActivity>()) else it
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    }
}
