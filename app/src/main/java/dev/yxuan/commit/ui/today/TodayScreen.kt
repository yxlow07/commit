package dev.yxuan.commit.ui.today

import dev.yxuan.commit.ui.components.reorderActions
import dev.yxuan.commit.ui.components.reorderDrag
import dev.yxuan.commit.ui.components.reorderLift
import dev.yxuan.commit.ui.components.ReorderHandle
import dev.yxuan.commit.ui.components.rememberReorderState
import dev.yxuan.commit.ui.workout.workoutSections
import dev.yxuan.commit.ui.workout.rememberWorkoutSections
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TodoRow
import androidx.compose.foundation.lazy.LazyListScope
import dev.yxuan.commit.ui.components.GlyphIcon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.rememberLazyListState
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.SectionHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.db.entity.TodoEntity
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.RoutineRepository
import dev.yxuan.commit.data.repo.TodoRepository
import dev.yxuan.commit.domain.TodoItem
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.commitments.CommitmentDialog
import dev.yxuan.commit.ui.common.NotificationsOffNotice
import dev.yxuan.commit.ui.common.openQuickAdd
import dev.yxuan.commit.ui.components.NavRow
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PrimaryRoundButton
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.format.DueLabels
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One commitment row on the Groups tab. [count] is the open todos (null for workouts and
 * collections); [trailing] is the large right-hand text: the count, or "34/50" for a collection.
 */
data class TodayRow(val commitment: CommitmentEntity, val detail: String, val count: Int?, val trailing: String? = count?.toString())

/** One open todo in the time view, with the commitment it belongs to. */
data class AgendaRow(val item: TodoItem, val commitment: CommitmentEntity)

data class TodayState(val summary: String, val rows: List<TodayRow>, val loaded: Boolean, val agenda: List<AgendaRow> = emptyList())

class TodayViewModel(
    private val commitments: CommitmentRepository,
    private val todoRepository: TodoRepository,
    routines: RoutineRepository,
    collections: dev.yxuan.commit.data.repo.CollectionRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<TodayState> = combine(
        commitments.observeActive(), todoRepository.observeOpenAll(), routines.observeSummaries(), collections.observeProgress(),
    ) { list, open, routineList, progress -> build(list, open, routineList, progress.associateBy { it.commitmentId }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState("", emptyList(), loaded = false))

    fun reorder(ids: List<String>) {
        viewModelScope.launch { commitments.reorder(ids) }
    }

    fun setDone(todoId: String, done: Boolean) {
        viewModelScope.launch { todoRepository.setDone(todoId, done) }
    }

    fun create(name: String, type: CommitmentType, color: Int) {
        viewModelScope.launch { commitments.create(name, type, color) }
    }

    private fun build(
        list: List<CommitmentEntity>,
        open: List<TodoEntity>,
        routineList: List<RoutineSummary>,
        progress: Map<String, dev.yxuan.commit.data.db.dao.CollectionProgress>,
    ): TodayState {
        val today = LocalDate.now(clock)
        val items = open.map { TodoItem.of(it, clock) }.filter { !it.isDone }
        val byCommitment = items.groupBy { it.todo.commitmentId }
        val late = items.count { DueLabels.isLate(it.dueAt, it.hasTime, clock) }
        val dueToday = items.count { !DueLabels.isLate(it.dueAt, it.hasTime, clock) && it.dueAt?.let { d -> day(d) == today } == true }
        val plannedToday = routineList.filter { Weekdays.isPlanned(it.routine.plannedDays, today) }.map { it.routine.name }

        val sentence = buildString {
            when {
                dueToday == 0 && late == 0 -> append("Nothing due today.")
                late == 0 -> append("$dueToday due today.")
                dueToday == 0 -> append("$late overdue.")
                else -> append("$dueToday due today, $late overdue.")
            }
            if (plannedToday.isNotEmpty()) append(" ${plannedToday.joinToString(" and ")} today.")
        }

        val rows = list.map { c ->
            when (c.type) {
                CommitmentType.WORKOUT -> {
                    val mine = routineList.filter { it.routine.commitmentId == c.id }
                    TodayRow(c, workoutDetail(mine, today), null)
                }
                CommitmentType.COLLECTION -> {
                    val p = progress[c.id]
                    val detail = when {
                        p == null || p.total == 0 -> "Nothing in it yet"
                        p.collected == p.total -> "All ${p.total} collected"
                        else -> "${p.total - p.collected} still to find"
                    }
                    TodayRow(c, detail, null, trailing = p?.let { "${it.collected}/${it.total}" })
                }
                CommitmentType.STANDARD -> {
                    val mine = byCommitment[c.id].orEmpty()
                    TodayRow(c, todoDetail(mine, today), mine.size)
                }
            }
        }
        val byId = list.associateBy { it.id }
        val agenda = items.sortedWith(TodoItem.ORDER).mapNotNull { i ->
            byId[i.todo.commitmentId]?.takeIf { it.type == CommitmentType.STANDARD }?.let { AgendaRow(i, it) }
        }
        return TodayState(sentence, rows, loaded = true, agenda = agenda)
    }

    private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate()

    /** "1 overdue, next due today", "Next due Fri", "No due dates", "Nothing open". */
    private fun todoDetail(items: List<TodoItem>, today: LocalDate): String {
        if (items.isEmpty()) return "Nothing open"
        val late = items.count { DueLabels.isLate(it.dueAt, it.hasTime, clock) }
        val next = items.filter { it.dueAt != null && !DueLabels.isLate(it.dueAt, it.hasTime, clock) }.minByOrNull { it.dueAt!! }
        val nextText = next?.let { "next due " + relativeDay(day(it.dueAt!!), today) }
        return when {
            late > 0 && nextText != null -> "$late overdue, $nextText"
            late > 0 -> "$late overdue"
            nextText != null -> nextText.replaceFirstChar { it.uppercase() }
            else -> "No due dates"
        }
    }

    /** "Push Day today", "Next: Pull Day Mon", "No workout planned". */
    private fun workoutDetail(routines: List<RoutineSummary>, today: LocalDate): String {
        if (routines.isEmpty()) return "No routines yet"
        routines.firstOrNull { Weekdays.isPlanned(it.routine.plannedDays, today) }?.let { return "${it.routine.name} today" }
        for (i in 1..7) {
            val d = today.plusDays(i.toLong())
            routines.firstOrNull { Weekdays.isPlanned(it.routine.plannedDays, d) }?.let {
                return "Next: ${it.routine.name} ${relativeDay(d, today)}"
            }
        }
        return "No workout planned"
    }

    private fun relativeDay(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d)) {
        0L -> "today"
        1L -> "tomorrow"
        in 2..6 -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        else -> d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

/** Today (nav: Today): the date, a one-line summary and one row per commitment. */
@Composable
fun TodayScreen(
    onOpenCommitment: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier,
    startCreating: Boolean = false,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = Commit.colors
    val t = Commit.type
    var creating by remember { mutableStateOf(startCreating) }
    var creatingType by remember { mutableStateOf(CommitmentType.STANDARD) }
    val today = LocalDate.now()

    // Commitments and routines reorder by dragging their grip, or holding the row.
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState)
    val workouts = rememberWorkoutSections(onOpenRoutine, listState)
    reorder.sync(state.rows.map { it.commitment.id })
    val rowsById = state.rows.associateBy { it.commitment.id }
    val shownRows = reorder.order.mapNotNull { rowsById[it] }
    val saveOrder: (List<String>) -> Unit = { viewModel.reorder(it) }

    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = dev.yxuan.commit.ui.components.ScreenPadding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The month sits on the numeral's baseline, so both blocks share one bottom line.
                        Text(today.dayOfMonth.toString(), style = t.displayXl.copy(lineHeight = 80.sp, letterSpacing = (-4).sp), color = c.ink, modifier = Modifier.alignBy(LastBaseline))
                        Column(Modifier.alignBy(LastBaseline)) {
                            Text(today.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())), style = t.bodyL.copy(fontWeight = FontWeight.Medium, lineHeight = 21.sp), color = c.ink)
                            Text(today.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())), style = t.bodyL.copy(lineHeight = 21.sp), color = c.muted)
                        }
                    }
                    PrimaryRoundButton(onClick = { openQuickAdd(context, null) }, contentDescription = "Add todo")
                }
                if (state.loaded && state.rows.isNotEmpty()) {
                    Text(state.summary, style = t.bodyL, color = c.textStrong, modifier = Modifier.widthIn(max = 300.dp))
                }
                NotificationsOffNotice()
            }
            Spacer(Modifier.height(Space.xxxl))
        }

        if (state.loaded && state.rows.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                    Text("Add your first commitment", style = t.bodyL, color = c.textStrong)
                    PrimaryButton("New commitment", onClick = { creatingType = CommitmentType.STANDARD; creating = true })
                }
            }
        }

        // Todo commitments first, then workout commitments under their own heading. Each group is
        // reordered on its own: drag by the handle, or hold anywhere on the row.
        val todoRows = shownRows.filter { it.commitment.type == CommitmentType.STANDARD }
        val collectionRows = shownRows.filter { it.commitment.type == CommitmentType.COLLECTION }

        fun group(rows: List<TodayRow>) {
            val groupIds = rows.map { it.commitment.id }
            items(rows, key = { it.commitment.id }) { row ->
                val accent = Color(row.commitment.color)
                val id = row.commitment.id
                NavRow(
                    leading = { ReorderHandle(reorder, id, groupIds, saveOrder) },
                    trailingColor = accent,
                    detailColor = lerp(c.muted, accent, 0.35f),
                    title = row.commitment.name,
                    detail = row.detail,
                    trailing = row.trailing,
                    titleStyle = t.titleDialog.copy(fontWeight = FontWeight.Medium),
                    trailingStyle = t.tabSelected.tnum(),
                    minHeight = 64.dp,
                    onClick = {
                        if (row.commitment.type == CommitmentType.COLLECTION) onOpenCollection(row.commitment.id)
                        else onOpenCommitment(row.commitment.id)
                    },
                    modifier = Modifier
                        .then(if (reorder.dragging == id) Modifier else Modifier.animateItem())
                        .reorderLift(reorder, id)
                        .reorderDrag(reorder, id, groupIds, saveOrder)
                        .padding(bottom = Space.xs)
                        .semantics { customActions = reorderActions(id, groupIds) { viewModel.reorder(mergeGroup(reorder.order, it)) } },
                )
            }
        }

        if (state.rows.isNotEmpty()) {
            item(key = "commitments-header") {
                SectionHeader("Commitments", Glyph.Plus, "New commitment", onAction = { creatingType = CommitmentType.STANDARD; creating = true })
            }
            group(todoRows)
        }
        workoutSections(workouts, onOpenRoutine, onOpenLive, topGap = state.rows.isNotEmpty())
        // Collections: standalone trackers, each with how much of it is collected.
        if (collectionRows.isNotEmpty()) {
            item(key = "collections-header") {
                SectionHeader("Collections", Glyph.Plus, "New collection", onAction = { creatingType = CommitmentType.COLLECTION; creating = true }, modifier = Modifier.padding(top = Space.xxl))
            }
            group(collectionRows)
        }
    }

    if (creating) {
        CommitmentDialog(
            initial = null,
            initialType = creatingType,
            onDismiss = { creating = false },
            onSave = { name, type, color, _ -> viewModel.create(name, type, color); creating = false },
        )
    }
}

/**
 * The Agenda: every open todo across commitments in due order, under Overdue, Today, Tomorrow,
 * This week, Later and No date (empty sections hidden). Rows are the shared todo row; the second
 * line names the commitment, tinted toward its colour.
 */
internal fun LazyListScope.timeSections(rows: List<AgendaRow>, onEdit: (String) -> Unit, onToggle: (String, Boolean) -> Unit) {
    val clock = Clock.systemDefaultZone()
    val today = LocalDate.now(clock)
    fun day(m: Long) = Instant.ofEpochMilli(m).atZone(clock.zone).toLocalDate()
    val sections = linkedMapOf<String, MutableList<AgendaRow>>()
    for (r in rows) {
        val d = r.item.dueAt
        val title = when {
            d == null -> "No date"
            DueLabels.isLate(d, r.item.hasTime, clock) -> "Overdue"
            else -> when (ChronoUnit.DAYS.between(today, day(d))) {
                0L -> "Today"
                1L -> "Tomorrow"
                in 2..6 -> "This week"
                else -> "Later"
            }
        }
        sections.getOrPut(title) { mutableListOf() } += r
    }
    if (rows.isEmpty()) {
        item(key = "time-empty") {
            Text("Nothing open.", style = Commit.type.bodyL, color = Commit.colors.textStrong)
        }
        return
    }
    for (title in listOf("Overdue", "Today", "Tomorrow", "This week", "Later", "No date")) {
        val list = sections[title] ?: continue
        item(key = "time-$title") {
            SectionHeading(title, Modifier.padding(top = Space.l, bottom = Space.xs), danger = title == "Overdue")
        }
        items(list, key = { "time-" + it.item.todo.id }) { r ->
            val i = r.item
            val second = listOfNotNull(
                r.commitment.name,
                if (i.isRecurring) dev.yxuan.commit.domain.Recurrence.label(i.todo) else null,
            ).joinToString(", ")
            TodoRow(
                due = DueLabels.open(i.dueAt, i.hasTime, clock),
                title = i.todo.title,
                checked = false,
                onCheckedChange = { onToggle(i.todo.id, it) },
                onClick = { onEdit(i.todo.id) },
                secondLine = second,
                secondLineColor = lerp(Commit.colors.muted, Color(r.commitment.color), 0.35f),
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** [order] with the members of one group put in [groupOrder]'s sequence, in the slots they held. */
internal fun mergeGroup(order: List<String>, groupOrder: List<String>): List<String> {
    val members = groupOrder.toSet()
    val next = groupOrder.iterator()
    return order.map { if (it in members) next.next() else it }
}
