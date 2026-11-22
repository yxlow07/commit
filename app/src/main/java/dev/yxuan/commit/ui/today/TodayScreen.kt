package dev.yxuan.commit.ui.today

import dev.yxuan.commit.ui.workout.workoutSections
import dev.yxuan.commit.ui.workout.workoutInProgress
import dev.yxuan.commit.ui.workout.rememberWorkoutSections
import dev.yxuan.commit.ui.components.SectionHeading
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

/** One commitment row on Today. [count] is null for workout commitments. */
data class TodayRow(val commitment: CommitmentEntity, val detail: String, val count: Int?)

data class TodayState(val summary: String, val rows: List<TodayRow>, val loaded: Boolean)

class TodayViewModel(
    private val commitments: CommitmentRepository,
    todos: TodoRepository,
    routines: RoutineRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<TodayState> = combine(
        commitments.observeActive(), todos.observeOpenAll(), routines.observeSummaries(),
    ) { list, open, routineList -> build(list, open, routineList) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState("", emptyList(), loaded = false))

    fun reorder(ids: List<String>) {
        viewModelScope.launch { commitments.reorder(ids) }
    }

    fun create(name: String, type: CommitmentType, color: Int) {
        viewModelScope.launch { commitments.create(name, type, color) }
    }

    private fun build(list: List<CommitmentEntity>, open: List<TodoEntity>, routineList: List<RoutineSummary>): TodayState {
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
            if (c.type == CommitmentType.WORKOUT) {
                val mine = routineList.filter { it.routine.commitmentId == c.id }
                TodayRow(c, workoutDetail(mine, today), null)
            } else {
                val mine = byCommitment[c.id].orEmpty()
                TodayRow(c, todoDetail(mine, today), mine.size)
            }
        }
        return TodayState(sentence, rows, loaded = true)
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
    val today = LocalDate.now()

    // Hold a commitment and drag it to reorder. While dragging, [order] is the list as shown; it's
    // saved when the finger lifts, and follows the database again otherwise.
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val workouts = rememberWorkoutSections(onOpenRoutine)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val order = remember { mutableStateListOf<String>() }
    val rowIds = state.rows.map { it.commitment.id }
    if (dragging == null && order.toList() != rowIds) { order.clear(); order.addAll(rowIds) }
    val rowsById = state.rows.associateBy { it.commitment.id }
    val shownRows = order.mapNotNull { rowsById[it] }
    var pendingFrom by remember { mutableStateOf<Int?>(null) }
    var orderAtStart by remember { mutableStateOf<List<String>>(emptyList()) }
    fun startDrag(id: String) {
        dragging = id
        dragOffset = 0f
        orderAtStart = order.toList()
        pendingFrom = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    /** Moves the dragged row by [dy]; it swaps places only with rows of its own group. */
    fun dragBy(id: String, dy: Float, groupIds: List<String>) {
        dragOffset += dy
        val visible = listState.layoutInfo.visibleItemsInfo
        val self = visible.firstOrNull { it.key == id } ?: return
        // After a swap, wait until the list has laid the row out in its new place.
        if (pendingFrom != null) { if (self.offset == pendingFrom) return else pendingFrom = null }
        val centre = (self.offset + dragOffset + self.size / 2f).toInt()
        val target = visible.firstOrNull { it.key != id && it.key in groupIds && centre in it.offset until it.offset + it.size } ?: return
        order.add(order.indexOf(target.key as String), order.removeAt(order.indexOf(id)))
        // The row now sits where the target was; keep it under the finger.
        dragOffset -= (target.offset - self.offset)
        pendingFrom = self.offset
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun endDrag() {
        if (dragging != null && order.toList() != orderAtStart) viewModel.reorder(order.toList())
        dragging = null
        dragOffset = 0f
    }

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
                    PrimaryButton("New commitment", onClick = { creating = true })
                }
            }
        }

        // Todo commitments first, then workout commitments under their own heading. Each group is
        // reordered on its own: drag by the handle, or hold anywhere on the row.
        val todoRows = shownRows.filter { it.commitment.type != CommitmentType.WORKOUT }
        workoutInProgress(workouts, onOpenLive)

        fun group(rows: List<TodayRow>) {
            val groupIds = rows.map { it.commitment.id }
            items(rows, key = { it.commitment.id }) { row ->
                val accent = Color(row.commitment.color)
                val id = row.commitment.id
                val lifted = dragging == id
                // The drag handlers live across reorders (restarting them would drop the drag), so
                // they read the group's current members through this.
                val currentGroup by rememberUpdatedState(groupIds)
                NavRow(
                    leading = {
                        // The handle: drag straight away, no hold needed.
                        Box(
                            Modifier
                                .size(width = 28.dp, height = 44.dp)
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = { startDrag(id) },
                                        onDrag = { change, amount -> change.consume(); dragBy(id, amount.y, currentGroup) },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                    )
                                },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            GlyphIcon(Glyph.Grip, c.faint, size = 16.dp)
                        }
                    },
                    trailingColor = accent,
                    detailColor = lerp(c.muted, accent, 0.35f),
                    title = row.commitment.name,
                    detail = row.detail,
                    trailing = row.count?.toString(),
                    titleStyle = t.titleDialog.copy(fontWeight = FontWeight.Medium),
                    trailingStyle = t.tabSelected.tnum(),
                    minHeight = 64.dp,
                    onClick = { onOpenCommitment(row.commitment.id) },
                    modifier = Modifier
                        .then(if (lifted) Modifier.zIndex(1f) else Modifier.animateItem())
                        .graphicsLayer {
                            translationY = if (lifted) dragOffset else 0f
                            if (lifted) { scaleX = 1.02f; scaleY = 1.02f }
                        }
                        .then(if (lifted) Modifier.shadow(8.dp, RoundedCornerShape(Commit.shapes.button)).background(c.surface, RoundedCornerShape(Commit.shapes.button)) else Modifier)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startDrag(id) },
                                onDrag = { change, amount -> change.consume(); dragBy(id, amount.y, currentGroup) },
                                onDragEnd = { endDrag() },
                                onDragCancel = { endDrag() },
                            )
                        }
                        .padding(bottom = Space.xs)
                        .semantics { customActions = moveActions(id, groupIds) { viewModel.reorder(mergeGroup(order, it)) } },
                )
            }
        }

        if (state.rows.isNotEmpty()) {
            item(key = "commitments-header") {
                SectionHeader("Commitments", Glyph.Plus, "New commitment", onAction = { creating = true })
            }
            group(todoRows)
        }
        workoutSections(workouts, onOpenRoutine, onOpenLive, topGap = state.rows.isNotEmpty())
    }

    if (creating) {
        CommitmentDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, type, color, _ -> viewModel.create(name, type, color); creating = false },
        )
    }
}

/** "Move up" / "Move down" for screen readers, which can't hold and drag. */
private fun moveActions(id: String, order: List<String>, save: (List<String>) -> Unit): List<CustomAccessibilityAction> {
    val i = order.indexOf(id)
    fun moved(to: Int) = order.toMutableList().apply { add(to, removeAt(i)) }
    return listOfNotNull(
        if (i > 0) CustomAccessibilityAction("Move up") { save(moved(i - 1)); true } else null,
        if (i in 0 until order.size - 1) CustomAccessibilityAction("Move down") { save(moved(i + 1)); true } else null,
    )
}

/** [order] with the members of one group put in [groupOrder]'s sequence, in the slots they held. */
private fun mergeGroup(order: List<String>, groupOrder: List<String>): List<String> {
    val members = groupOrder.toSet()
    val next = groupOrder.iterator()
    return order.map { if (it in members) next.next() else it }
}
