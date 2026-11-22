package dev.yxuan.commit.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.R
import dev.yxuan.commit.appContainer
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.domain.Recurrence
import dev.yxuan.commit.domain.TodoItem
import dev.yxuan.commit.quickadd.QuickAddActivity
import dev.yxuan.commit.ui.format.DueLabels
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Per-widget-instance state: the selected commitment (phone-only, not synced). */
val SelectedCommitmentKey = stringPreferencesKey("selected_commitment_id")

/** Per-widget-instance state: todos ticked on this tab, kept visible (checked) until the tab changes. */
val RecentlyDoneKey = stringSetPreferencesKey("recently_done")

val TodoIdParam = ActionParameters.Key<String>("todo_id")
val CommitmentIdParam = ActionParameters.Key<String>("commitment_id")
val DoneParam = ActionParameters.Key<Boolean>("done")
val RowParam = ActionParameters.Key<Int>("row")
val NewCommitmentParam = ActionParameters.Key<Boolean>(MainActivity.EXTRA_NEW_COMMITMENT)
val OpenWorkoutsParam = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_WORKOUTS)

private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 250.dp)

/**
 * Home-screen widget (F3, design spec "Widget"): commitment tabs, the selected commitment's todos
 * (due left, checkbox right), and a footer with the open count and a round add button. Reads Room
 * only, never the network.
 */
class CommitWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Exact: the layout (small 3x2, medium 4x3, large 4x4+) and the number of rows that fit come
    // from the real size; Responsive would only report the nearest size bucket.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer
        val clock = container.clock
        val tabsFlow = container.commitmentRepository.observeWidgetTabs()
        // One-off todos finished in the last day are included so a just-ticked row can stay visible.
        val todosFlow = container.todoRepository.observeForWidget(clock.millis() - 24 * 60 * 60 * 1000L)
        val routinesFlow = container.routineRepository.observeSummaries()
        val liveFlow = container.workoutRepository.observeLive()
        val themeFlow = container.themeRepository.observeActive()
        // Load once up front so the first frame already has data (no empty flash).
        val initialTabs = tabsFlow.first()
        val initialTodos = todosFlow.first()
        val initialRoutines = routinesFlow.first()
        val initialLive = liveFlow.first()
        val initialTheme = themeFlow.first()

        provideContent {
            val tabs by tabsFlow.collectAsState(initialTabs)
            val todos by todosFlow.collectAsState(initialTodos)
            val routines by routinesFlow.collectAsState(initialRoutines)
            val live by liveFlow.collectAsState(initialLive)
            val theme by themeFlow.collectAsState(initialTheme)
            val prefs = currentState<Preferences>()
            val selectedId = prefs[SelectedCommitmentKey]
            // "id|row" entries: ticked rows and the position they were in.
            val recentlyDone = prefs[RecentlyDoneKey].orEmpty().mapNotNull { e ->
                e.split('|').takeIf { it.size == 2 }?.let { (tid, row) -> tid to (row.toIntOrNull() ?: 0) }
            }.toMap()
            // Fall back to the first tab if the stored one was archived, deleted or hidden.
            val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.firstOrNull()
            val colors = remember(theme) { WidgetColors.of(theme) }

            CompositionLocalProvider(LocalWidgetColors provides colors) {
                val panel = GlanceModifier.fillMaxSize().appWidgetBackground().background(colors.panel)
                    .let { if (theme.usesSystemRadius()) it.cornerRadius(android.R.dimen.system_app_widget_background_radius) else it.cornerRadius(theme.spec.cornerRadius.dp) }
                    .padding(start = 20.dp, end = 6.dp, top = 16.dp, bottom = 14.dp)
                Column(panel) {
                    if (selected == null) {
                        NoCommitments()
                        return@Column
                    }
                    TabRow(tabs, selected)
                    if (selected.type == CommitmentType.WORKOUT) {
                        Spacer(GlanceModifier.height(10.dp))
                        WorkoutPanel(routines.filter { it.routine.commitmentId == selected.id }, live)
                    } else {
                        // Recalculated on every redraw, so chores reappear after the midnight alarm (F4.4).
                        val mine = todos.filter { it.commitmentId == selected.id }.map { TodoItem.of(it, clock) }
                        val shown = withTickedRows(mine, recentlyDone)
                        TodoArea(selected, shown, clock)
                    }
                }
            }
        }
    }
}

/**
 * Open todos in widget order, with rows ticked on this tab put back at the position they were in
 * (a ticked chore's next due date would otherwise move it down the list).
 */
internal fun withTickedRows(items: List<TodoItem>, ticked: Map<String, Int>): List<TodoItem> {
    val result = items.filter { !it.isDone && it.todo.id !in ticked }.sortedWith(TodoItem.ORDER).toMutableList()
    items.filter { it.isDone && it.todo.id in ticked }
        .sortedBy { ticked.getValue(it.todo.id) }
        .forEach { result.add(ticked.getValue(it.todo.id).coerceIn(0, result.size), it) }
    return result
}

// ---- Tabs ----

/**
 * Rough text width for fitting tabs (Glance can't measure). Measured on the emulator: about 0.5 em
 * per character for the large light tab, 0.47 em for the 15 sp medium ones.
 */
private fun textWidth(text: String, size: TextUnit) = (text.length * size.value * if (size.value >= 20f) 0.5f else 0.47f).dp

/** A tab in the visible window; [partial] tabs are cut off at the right edge to show there's more. */
internal data class TabSlot(val index: Int, val partial: Boolean = false, val width: Float = 0f)

/**
 * Which tabs the row shows, as if it were a scrolled strip (widgets can't scroll sideways). The
 * window starts one tab before the selected one, so you can always step back; near the end it slides
 * left to stay full. After the tabs that fit, the next one is shown cut off when at least
 * [minPartial] is left. [widths] are each tab's full width at its style, in dp.
 */
internal fun tabWindow(widths: List<Float>, selected: Int, available: Float, gap: Float, minPartial: Float = 36f): List<TabSlot> {
    if (widths.isEmpty()) return emptyList()
    fun fitFrom(start: Int): Int { // index after the last tab that fits fully
        var used = 0f
        var i = start
        while (i < widths.size) {
            val add = widths[i] + if (i > start) gap else 0f
            if (used + add > available && i > start) break
            used += add
            i++
        }
        return i
    }
    var start = (selected - 1).coerceAtLeast(0)
    // The selected tab must fit fully; drop the tab before it if not.
    if (fitFrom(start) <= selected) start = selected
    // Scrolled to the end: slide left while a whole earlier tab still fits.
    if (fitFrom(start) == widths.size) {
        while (start > 0 && fitFrom(start - 1) == widths.size) start--
    }
    val end = fitFrom(start)
    val slots = (start until end).map { TabSlot(it) }.toMutableList()
    if (end < widths.size) {
        val used = (start until end).sumOf { widths[it].toDouble() }.toFloat() + gap * (end - start)
        val left = available - used
        when {
            left >= minPartial -> slots += TabSlot(end, partial = true, width = left)
            // No room for the next one: cut the last tab instead (never the selected one), so the
            // row still shows there's more.
            slots.size > 1 && slots.last().index != selected -> {
                val last = slots.removeAt(slots.lastIndex)
                slots += TabSlot(last.index, partial = true, width = widths[last.index] + left - 1f)
            }
        }
    }
    return slots
}

/**
 * One row of names: selected 30 sp light `ink`, others 15 sp regular in a lighter grey, bottoms aligned (small
 * tabs get 4 dp so their baselines meet). It behaves like a scrolled strip ([tabWindow]): tapping a
 * tab further along selects it and the row slides to it. On a narrow widget the selected tab drops
 * to 24 sp.
 */
@Composable
private fun TabRow(tabs: List<CommitmentEntity>, selected: CommitmentEntity) {
    val c = LocalWidgetColors.current
    val available = (LocalSize.current.width - 36.dp).value
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val bigSize = if (textWidth(selected.name, 30.sp).value > available * 0.6f) 24.sp else 30.sp
    val widths = tabs.mapIndexed { i, t -> textWidth(t.name, if (i == selectedIndex) bigSize else 15.sp).value }
    val slots = tabWindow(widths, selectedIndex, available, gap = 14f)

    // 12 dp above and below for a 44 dp hit area; a short widget showing a workout needs the room.
    val tabPad = if (selected.type == CommitmentType.WORKOUT && LocalSize.current.height < COMPACT_WORKOUT_HEIGHT) 4.dp else 12.dp
    Row(GlanceModifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.Bottom) {
        slots.forEachIndexed { n, slot ->
            if (n > 0) Spacer(GlanceModifier.width(14.dp))
            val tab = tabs[slot.index]
            val on = slot.index == selectedIndex
            val tap = actionRunCallback<SelectTabAction>(actionParametersOf(CommitmentIdParam to tab.id))
            if (on) {
                // Selected: a subtle short dash under the name. Its 5 dp (gap + bar) come out of
                // the bottom padding, so the baseline still meets the small tabs'.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier
                        .padding(top = tabPad, bottom = (tabPad - 5.dp).coerceAtLeast(0.dp))
                        .clickable(tap)
                        .semantics { contentDescription = tab.name + ", selected tab" },
                ) {
                    Text(tab.name, maxLines = 1, style = TextStyle(color = c.ink, fontSize = bigSize, fontFamily = WidgetLight))
                    Spacer(GlanceModifier.height(3.dp))
                    // A short dash centred under the name, in the lightest grey: a hint, not a highlight.
                    Box(GlanceModifier.width(18.dp).height(2.dp).cornerRadius(1.dp).background(c.track)) {}
                }
            } else {
                // Unselected: light grey, regular weight.
                Text(
                    tab.name,
                    maxLines = 1,
                    style = TextStyle(color = c.tabIdle, fontSize = 15.sp, fontWeight = FontWeight.Normal),
                    modifier = GlanceModifier
                        .let { if (slot.partial) it.width(slot.width.dp) else it }
                        .padding(top = tabPad, bottom = tabPad + 4.dp)
                        .clickable(tap)
                        .semantics { contentDescription = tab.name + ", tab" },
                )
            }
        }
    }
}

// ---- Todos ----

@Composable
private fun TodoArea(commitment: CommitmentEntity, items: List<TodoItem>, clock: Clock) {
    val c = LocalWidgetColors.current
    val size = LocalSize.current
    val small = size.height < MEDIUM.height || size.width < MEDIUM.width
    val large = !small && size.height >= LARGE.height
    // Rows that fit between the tabs (~60 dp) and the footer (44 dp), inside 30 dp of padding.
    val fit = ((size.height - 30.dp - 60.dp - 44.dp) / 52.dp).toInt().coerceAtLeast(1)
    val maxRows = if (small) minOf(2, fit) else minOf(4, fit)
    val open = items.count { !it.isDone }
    val late = items.count { !it.isDone && DueLabels.isLate(it.dueAt, it.hasTime, clock) }

    Column(GlanceModifier.fillMaxSize()) {
        Box(GlanceModifier.fillMaxWidth().defaultWeight()) {
            when {
                items.isEmpty() -> Text(
                    "All done for ${commitment.name}.",
                    style = TextStyle(color = c.textStrong, fontSize = 15.sp),
                    modifier = GlanceModifier.padding(start = 56.dp, top = 8.dp),
                )
                large && items.size > fit -> LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(items.withIndex().toList(), itemId = { it.value.todo.id.hashCode().toLong() }) { (i, item) -> TodoRow(item, i, clock) }
                }
                else -> Column(GlanceModifier.fillMaxWidth()) { items.take(if (large) fit else maxRows).forEachIndexed { i, item -> TodoRow(item, i, clock) } }
            }
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!small) {
                val summary = when {
                    open == 0 -> "All done"
                    late > 0 -> "$open open, $late overdue"
                    else -> "$open open"
                }
                Text(summary, style = TextStyle(color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.defaultWeight())
            AddButton(commitment.id)
        }
    }
}

/** Due label (56 dp column), title and optional recurrence line, checkbox on the right. */
@Composable
private fun TodoRow(item: TodoItem, row: Int, clock: Clock) {
    val c = LocalWidgetColors.current
    val todo = item.todo
    val due = DueLabels.open(item.dueAt, item.hasTime, clock)
    val checked = item.isDone
    Row(GlanceModifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            GlanceModifier.defaultWeight().height(52.dp)
                // Opens the small edit popup over the home screen, not the full app.
                .clickable(actionStartActivity<QuickAddActivity>(actionParametersOf(TodoIdParam to todo.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (checked) "" else due.text,
                maxLines = 1,
                style = TextStyle(color = if (due.late) c.danger else c.muted, fontSize = 14.sp),
                modifier = GlanceModifier.width(56.dp),
            )
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    todo.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (checked) c.muted else c.ink,
                        fontSize = 16.sp,
                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                )
                if (item.isRecurring && !checked) {
                    Text(Recurrence.label(todo), maxLines = 1, style = TextStyle(color = c.muted, fontSize = 13.sp))
                }
            }
        }
        Checkbox(todo.id, row, todo.title, checked)
    }
}

/** 44 dp hit area holding a 22 dp ring, or an `ink` disc with an `onInk` tick when checked. */
@Composable
private fun Checkbox(todoId: String, row: Int, title: String, checked: Boolean) {
    val c = LocalWidgetColors.current
    Box(
        GlanceModifier.size(44.dp)
            .clickable(actionRunCallback<ToggleTodoAction>(actionParametersOf(TodoIdParam to todoId, DoneParam to !checked, RowParam to row)))
            .semantics { contentDescription = (if (checked) "Mark not done: " else "Mark done: ") + title },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(GlanceModifier.size(22.dp).cornerRadius(11.dp).background(c.ink), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.widget_tick), contentDescription = null, colorFilter = ColorFilter.tint(c.onInk), modifier = GlanceModifier.size(12.dp))
            }
        } else {
            Image(ImageProvider(R.drawable.widget_ring), contentDescription = null, colorFilter = ColorFilter.tint(c.ink), modifier = GlanceModifier.size(22.dp))
        }
    }
}

/** Primary round add button (44 dp) → quick-add popup over the home screen, on this tab. */
@Composable
private fun AddButton(commitmentId: String) {
    val c = LocalWidgetColors.current
    Box(
        GlanceModifier.size(44.dp).cornerRadius(WIDGET_BUTTON_RADIUS).background(c.ink)
            .clickable(actionStartActivity<QuickAddActivity>(actionParametersOf(CommitmentIdParam to commitmentId)))
            .semantics { contentDescription = "Add todo" },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.widget_plus), contentDescription = null, colorFilter = ColorFilter.tint(c.onInk), modifier = GlanceModifier.size(18.dp))
    }
    Spacer(GlanceModifier.width(6.dp))
}

/** "Add your first commitment" with a button that opens the app on the new-commitment screen. */
@Composable
private fun NoCommitments() {
    val c = LocalWidgetColors.current
    Column(GlanceModifier.fillMaxSize().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Add your first commitment", style = TextStyle(color = c.textStrong, fontSize = 15.sp))
        Spacer(GlanceModifier.height(12.dp))
        WidgetButton("New commitment", actionStartActivity<MainActivity>(actionParametersOf(NewCommitmentParam to true)), primary = true)
    }
}
