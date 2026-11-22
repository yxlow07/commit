package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.DeviceClock
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.domain.Recurrence
import dev.yxuan.commit.domain.TodoItem
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.commitments.CommitmentDialog
import dev.yxuan.commit.ui.common.openQuickAdd
import dev.yxuan.commit.ui.components.PrimaryRoundButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.components.TextTabs
import dev.yxuan.commit.ui.components.TodoRow
import dev.yxuan.commit.ui.format.DueLabels
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A section of rows: heading, whether it's the Overdue one, and rows with their second line. */
private data class Section(val title: String, val danger: Boolean, val rows: List<Row>)
private data class Row(val item: TodoItem, val second: String?, val checked: Boolean, val doneAt: Long? = null)

/** Commitment detail (pushed): title, "4 open, 1 overdue", Open / Done, sections, + button (labelled "Add to Study"). */
@Composable
fun CommitmentDetailScreen(
    onBack: () -> Unit,
    onEditTodo: (String) -> Unit,
    onOpenWorkouts: () -> Unit,
    viewModel: CommitmentDetailViewModel = viewModel(factory = AppViewModelFactory),
) {
    val commitment by viewModel.commitment.collectAsStateWithLifecycle()
    val openList by viewModel.open.collectAsStateWithLifecycle()
    val doneList by viewModel.done.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val c = Commit.colors
    val clock: Clock = DeviceClock

    // Workout commitments hold routines, which live on the Workouts screen (F1.2, F5.1).
    LaunchedEffect(commitment?.type) {
        if (commitment?.type == CommitmentType.WORKOUT) onOpenWorkouts()
    }
    LaunchedEffect(commitment?.archived) {
        if (commitment?.archived == true) onBack()
    }

    val items = openList.orEmpty().map { TodoItem.of(it, clock) }
    val open = items.filterNot { it.isDone }
    val lateCount = open.count { DueLabels.isLate(it.dueAt, it.hasTime, clock) }
    val subtitle = when {
        openList == null -> ""
        open.isEmpty() -> "Nothing open"
        lateCount > 0 -> "${open.size} open, $lateCount overdue"
        else -> "${open.size} open"
    }
    val sections = if (tab == 0) openSections(open, clock) else doneSections(items.filter { it.isDone }, doneList.map { TodoItem.of(it, clock) }, clock)
    val name = commitment?.name ?: ""

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
            item {
                PushedTopBar(onBack = onBack, backLabel = "Back to Today") {
                    TextAction("Edit", onClick = { editing = true })
                }
                Column(Modifier.padding(horizontal = Space.screenSide).padding(top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    ScreenTitle(name)
                    Text(subtitle, style = Commit.type.meta, color = c.muted)
                }
                TextTabs(listOf("Open", "Done"), tab, { tab = it }, modifier = Modifier.padding(horizontal = Space.screenSide).padding(top = Space.l))
                Spacer(Modifier.height(Space.s))
            }
            if (openList != null && sections.isEmpty()) {
                item {
                    Text(
                        if (tab == 0) "All done for $name." else "Nothing finished yet.",
                        style = Commit.type.body,
                        color = c.textStrong,
                        modifier = Modifier.padding(horizontal = Space.screenSide).padding(start = Space.dueColumn, top = Space.s),
                    )
                }
            }
            sections.forEach { section ->
                item(key = "h-${section.title}") {
                    SectionHeading(section.title, danger = section.danger, modifier = Modifier.padding(horizontal = Space.screenSide).padding(top = Space.l))
                }
                items(section.rows, key = { "${section.title}-${it.item.todo.id}" }) { row ->
                    TodoRow(
                        due = if (row.doneAt != null) DueLabels.done(row.doneAt, clock) else DueLabels.open(row.item.dueAt, row.item.hasTime, clock),
                        title = row.item.todo.title,
                        checked = row.checked,
                        onCheckedChange = { viewModel.setDone(row.item.todo.id, it) },
                        onClick = { onEditTodo(row.item.todo.id) },
                        secondLine = row.second,
                        modifier = Modifier.padding(horizontal = Space.screenSide).animateItem(),
                    )
                }
            }
        }
        if (commitment != null) {
            // Just the + (as on Today); the name is in the label for screen readers.
            PrimaryRoundButton(
                contentDescription = "Add to $name",
                onClick = { openQuickAdd(context, viewModel.commitmentId) },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = Space.screenSide, bottom = Space.xxl),
            )
        }
    }

    if (editing && commitment != null) {
        CommitmentDialog(
            initial = commitment,
            onDismiss = { editing = false },
            onSave = { n, _, color, show -> viewModel.update(n, color, show); editing = false },
            onMove = { viewModel.move(it) },
            onArchive = { viewModel.archive(); editing = false },
        )
    }
}

/** Overdue, This week (today and the next 6 days), Later, No date. Empty sections are hidden. */
private fun openSections(open: List<TodoItem>, clock: Clock): List<Section> {
    val today = LocalDate.now(clock)
    fun day(m: Long) = Instant.ofEpochMilli(m).atZone(clock.zone).toLocalDate()
    val sorted = open.sortedWith(TodoItem.ORDER)
    val late = sorted.filter { DueLabels.isLate(it.dueAt, it.hasTime, clock) }
    val rest = sorted - late.toSet()
    val week = rest.filter { it.dueAt != null && ChronoUnit.DAYS.between(today, day(it.dueAt)) <= 6 }
    val later = rest.filter { it.dueAt != null } - week.toSet()
    val undated = rest.filter { it.dueAt == null }
    fun second(i: TodoItem): String? {
        val parts = mutableListOf<String>()
        if (i.isRecurring) parts += Recurrence.label(i.todo)
        val d = i.dueAt
        when {
            d == null -> Unit
            DueLabels.isLate(d, i.hasTime, clock) -> parts += "was due " + DueLabels.full(d, i.hasTime, clock)
            day(d) != today -> parts += DueLabels.full(d, i.hasTime, clock)
            i.hasTime -> parts += "Today" // the left column shows the time
        }
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { null }
    }
    return listOf(
        Section("Overdue", true, late.map { Row(it, second(it), false) }),
        Section("This week", false, week.map { Row(it, second(it), false) }),
        Section("Later", false, later.map { Row(it, second(it), false) }),
        Section("No date", false, undated.map { Row(it, second(it), false) }),
    ).filter { it.rows.isNotEmpty() }
}

/**
 * Done: grouped by week, newest first. One-off todos say "On time", "1 day late" or "No due date";
 * chores waiting for their next due day say when that is.
 */
private fun doneSections(doneChores: List<TodoItem>, doneTodos: List<TodoItem>, clock: Clock): List<Section> {
    val today = LocalDate.now(clock)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val rows = doneChores.mapNotNull { i ->
        val at = i.todo.lastDoneAt ?: return@mapNotNull null
        val next = i.dueAt?.let { due -> DueLabels.open(due, false, clock).text.let { if (it == "Today") "today" else it } }
        Row(i, listOfNotNull(Recurrence.label(i.todo), next?.let { "next $it" }).joinToString(", "), true, at)
    } + doneTodos.mapNotNull { i ->
        val at = i.todo.doneAt ?: return@mapNotNull null
        Row(i, DueLabels.timeliness(i.todo.dueAt, i.todo.hasTime, at, clock), true, at)
    }
    fun weekTitle(at: Long): String {
        val d = Instant.ofEpochMilli(at).atZone(clock.zone).toLocalDate()
        val weekStart = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (weekStart) {
            monday -> "This week"
            monday.minusWeeks(1) -> "Last week"
            else -> "Week of " + weekStart.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }
    }
    return rows.sortedByDescending { it.doneAt }
        .groupBy { weekTitle(it.doneAt!!) }
        .map { (title, list) -> Section(title, false, list) }
}
