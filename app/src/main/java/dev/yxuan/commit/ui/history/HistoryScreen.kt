package dev.yxuan.commit.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.IconAction
import dev.yxuan.commit.ui.workout.DeleteSessionDialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.ExerciseSessionPoint
import dev.yxuan.commit.data.db.dao.SessionSummary
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.text.font.FontWeight
import dev.yxuan.commit.domain.Stats
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.ScreenPadding
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.components.TextTabs
import dev.yxuan.commit.ui.format.DueLabels
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil

/** Weeks in the heatmap (12 columns × 7 rows). */
private const val HEATMAP_WEEKS = 12

/** History (nav): Tasks and Workouts as text tabs on the title's baseline. */
@Composable
fun HistoryScreen(
    onOpenSession: (String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelFactory),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val completions by viewModel.completions.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val points by viewModel.exercisePoints.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var exercise by rememberSaveable { mutableStateOf<String?>(null) }
    var byRoutine by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SessionSummary?>(null) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = ScreenPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                ScreenTitle("History", Modifier.weight(1f))
                TextTabs(listOf("Tasks", "Workouts"), tab, { tab = it }, style = Commit.type.label, gap = 18.dp, dash = true)
            }
            Spacer(Modifier.height(Space.l))
        }
        if (tab == 0) tasks(completions.orEmpty(), filter, { filter = it }, viewModel.clock, onOpenDay)
        else workouts(points, sessions.orEmpty(), exercise, { exercise = it }, onOpenSession, viewModel.clock, byRoutine, { byRoutine = it }, { deleting = it })
    }

    deleting?.let { s ->
        DeleteSessionDialog(s, onDismiss = { deleting = null }) {
            viewModel.deleteSession(s.session.id)
            deleting = null
        }
    }
}

// ---- Tasks ----

private fun LazyListScope.tasks(rows: List<CompletionRow>, filter: String?, onFilter: (String?) -> Unit, clock: Clock, onOpenDay: (LocalDate) -> Unit) {
    val commitments = rows.distinctBy { it.commitmentId }.map { it.commitmentId to it.commitmentName }
    val shown = if (filter == null) rows else rows.filter { it.commitmentId == filter }
    item {
        if (commitments.size > 1) {
            val names = listOf("All") + commitments.map { it.second }
            val index = if (filter == null) 0 else commitments.indexOfFirst { it.first == filter } + 1
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextTabs(names, index, { i -> onFilter(if (i == 0) null else commitments[i - 1].first) }, style = Commit.type.label, gap = 18.dp)
            }
            Spacer(Modifier.height(Space.m))
        }
        Heatmap(shown, clock, onOpenDay)
        Spacer(Modifier.height(Space.section))
    }
    if (shown.isEmpty()) {
        item { Text("Nothing finished yet.", style = Commit.type.body, color = Commit.colors.textStrong) }
        return
    }
    val late = Stats.daysLate(rows, clock.zone)
    val streaks = Stats.currentStreaks(rows, clock)
    // The streak note goes on each chore's latest completion only.
    val latestPerChore = rows.filter { it.todoId in streaks }.groupBy { it.todoId }.mapValues { (_, l) -> l.maxOf { it.doneAt } }
    // Days are separated by space, not lines (the app draws no dividers): a wide gap above every
    // day after the first makes each day read as its own group.
    shown.sortedByDescending { it.doneAt }.groupBy { it.doneAt.day(clock) }.entries.forEachIndexed { i, (day, list) ->
        item(key = "d$day") { SectionHeading(dayHeading(day, clock), Modifier.padding(top = if (i == 0) Space.s else Space.xxxl)) }
        items(list, key = { it.completionId }) { r ->
            val lateDays = late[r.completionId]
            val note = when {
                lateDays != null -> if (lateDays == 0) "Late" else DueLabels.lateDays(lateDays)
                latestPerChore[r.todoId] == r.doneAt && (streaks[r.todoId] ?: 0) >= 2 -> "${streaks[r.todoId]} in a row"
                else -> null
            }
            Row(Modifier.fillMaxWidth().heightIn(min = Space.rowWidget), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(r.title, style = Commit.type.body, color = Commit.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.commitmentName}, ${time(r.doneAt, clock)}", style = Commit.type.meta.tnum(), color = Commit.colors.muted)
                }
                if (note != null) {
                    Text(note, style = Commit.type.meta, color = if (lateDays != null) Commit.colors.danger else Commit.colors.muted, modifier = Modifier.padding(start = Space.m))
                }
            }
        }
    }
}

/**
 * 12 columns (weeks, oldest left) × 7 rows (Mon–Sun), 3 dp gaps, radius 3, five shades. Tapping a
 * day opens its recap (what got done, the workouts, what was missed).
 */
@Composable
private fun Heatmap(rows: List<CompletionRow>, clock: Clock, onOpenDay: (LocalDate) -> Unit) {
    val c = Commit.colors
    val today = LocalDate.now(clock)
    val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val start = thisMonday.minusWeeks((HEATMAP_WEEKS - 1).toLong())
    val counts = Stats.perDay(rows, clock.zone)
    val max = counts.filterKeys { !it.isBefore(start) }.values.maxOrNull() ?: 0
    val thisWeek = counts.filterKeys { !it.isBefore(thisMonday) }.values.sum()
    val shades = c.heat
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.semantics { contentDescription = "Tasks finished per day, last $HEATMAP_WEEKS weeks. $thisWeek this week. Tap a day for its recap." },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (dow in 0 until 7) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (week in 0 until HEATMAP_WEEKS) {
                        val day = start.plusWeeks(week.toLong()).plusDays(dow.toLong())
                        val n = counts[day] ?: 0
                        val level = if (n == 0 || max == 0) 0 else ceil(n * 4.0 / max).toInt().coerceIn(1, 4)
                        val future = day.isAfter(today)
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(Commit.shapes.heatCell))
                                .background(if (future) c.background else shades[level])
                                .then(
                                    if (future) Modifier
                                    else Modifier.clickable(onClickLabel = "Open recap") { onOpenDay(day) }
                                        .semantics { contentDescription = "${DueLabels.date(day)}, $n finished" }
                                )
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("$HEATMAP_WEEKS weeks ago", style = Commit.type.micro, color = c.muted, modifier = Modifier.weight(1f))
            Text("Tap a day for its recap", style = Commit.type.micro, color = c.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Text("This week", style = Commit.type.micro, color = c.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        // Legend: this week's count, then a Less-to-More gradient through the heatmap's shades.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$thisWeek this week", style = Commit.type.meta.tnum(), color = c.textStrong, modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Lighter squares mean fewer finished, darker mean more" },
            ) {
                Text("Less", style = Commit.type.micro, color = c.muted)
                Box(
                    Modifier.width(88.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(shades))
                )
                Text("More", style = Commit.type.micro, color = c.muted)
            }
        }
    }
}

// ---- Workouts ----

private fun LazyListScope.workouts(
    points: List<ExerciseSessionPoint>,
    sessions: List<SessionSummary>,
    selected: String?,
    onSelect: (String) -> Unit,
    onOpenSession: (String) -> Unit,
    clock: Clock,
    byRoutine: Boolean,
    onByRoutine: (Boolean) -> Unit,
    onDelete: (SessionSummary) -> Unit,
) {
    // Exercises by most recent use.
    val names = points.sortedByDescending { it.startedAt }.map { it.exerciseName }.distinct()
    val current = selected?.takeIf { it in names } ?: names.firstOrNull()
    item {
        if (names.isNotEmpty() && current != null) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextTabs(names, names.indexOf(current), { onSelect(names[it]) }, style = Commit.type.label, gap = 18.dp)
            }
            Spacer(Modifier.height(Space.m))
            Progress(points.filter { it.exerciseName == current }.sortedBy { it.startedAt }, clock)
            Spacer(Modifier.height(Space.section))
        }
    }
    if (sessions.isEmpty()) {
        item { Text("No workouts logged yet.", style = Commit.type.body, color = Commit.colors.textStrong) }
        return
    }
    // Every session newest first, or grouped under each routine (routines by their latest session).
    item(key = "sessions-toggle") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            SectionHeading("Workouts", Modifier.weight(1f))
            TextTabs(listOf("Sessions", "By routine"), if (byRoutine) 1 else 0, { onByRoutine(it == 1) }, style = Commit.type.label, gap = 18.dp)
        }
    }
    if (!byRoutine) {
        items(sessions, key = { it.session.id }) { s -> SessionRow(s, s.routineName, clock, onOpenSession, onDelete) }
    } else {
        sessions.groupBy { it.session.routineId }.forEach { (routineId, list) ->
            item(key = "routine-$routineId") {
                Text(list.first().routineName, style = Commit.type.body.copy(fontWeight = FontWeight.Bold), color = Commit.colors.ink, modifier = Modifier.padding(top = Space.l, bottom = Space.xs))
            }
            items(list, key = { "r-" + it.session.id }) { s -> SessionRow(s, null, clock, onOpenSession, onDelete) }
        }
    }
}

/** One session: the routine (or, grouped by routine, the date) over "52 min, 16 sets, 3,450 kg"; bin to delete. */
@Composable
private fun LazyItemScope.SessionRow(s: SessionSummary, title: String?, clock: Clock, onOpen: (String) -> Unit, onDelete: (SessionSummary) -> Unit) {
    val c = Commit.colors
    val whenText = "${WorkoutFormat.sessionDate(s, clock.zone)}, ${WorkoutFormat.sessionTime(s, clock.zone)}"
    Row(
        Modifier.animateItem().fillMaxWidth().heightIn(min = Space.rowApp).clickable { onOpen(s.session.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title ?: whenText, style = Commit.type.body.tnum(), color = c.ink)
            val meta = "${WorkoutFormat.sessionMeta(s)}, ${WorkoutFormat.volume(s.volume)}"
            Text(if (title != null) "$whenText, $meta" else meta, style = Commit.type.meta.tnum(), color = c.muted)
        }
        IconAction(Glyph.Trash, "Delete ${s.routineName} session of $whenText", { onDelete(s) }, tint = c.muted, glyphSize = 18.dp, modifier = Modifier.offset(x = 12.dp))
    }
}

/** Best set in `display.l`, then a 2 dp `ink` line of best weight per session with a dot on the latest. */
@Composable
private fun Progress(points: List<ExerciseSessionPoint>, clock: Clock) {
    if (points.isEmpty()) return
    val c = Commit.colors
    val best = points.maxWith(compareBy<ExerciseSessionPoint> { it.bestWeight }.thenBy { it.bestReps })
    val since = points.first { it.bestWeight >= best.bestWeight }.startedAt
    val min = points.minOf { it.bestWeight }
    val max = points.maxOf { it.bestWeight }
    val weeksAgo = ChronoUnit.WEEKS.between(points.first().startedAt.day(clock), LocalDate.now(clock))
    val bestText = if (best.bestReps > 0) "${WorkoutFormat.weight(best.bestWeight)} × ${best.bestReps}" else WorkoutFormat.weight(best.bestWeight)
    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(bestText, style = Commit.type.displayL, color = c.ink)
            Text("Best set, since ${since.day(clock).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}", style = Commit.type.meta, color = c.muted)
        }
        val description = "${points.first().exerciseName} best weight over ${points.size} sessions, ${WorkoutFormat.weight(min)} to ${WorkoutFormat.weight(max)}"
        Canvas(Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription = description }) {
            val w = size.width
            val h = size.height
            val baseline = h - 4.dp.toPx()
            drawLine(c.track, Offset(0f, baseline), Offset(w, baseline), 1.dp.toPx())
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            val step = if (points.size > 1) (w - 8.dp.toPx()) / (points.size - 1) else 0f
            val pts = points.mapIndexed { i, p ->
                Offset(4.dp.toPx() + i * step, (baseline - 6.dp.toPx()) - ((p.bestWeight - min) / span).toFloat() * (baseline - 16.dp.toPx()))
            }
            if (pts.size > 1) {
                val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                drawPath(path, c.ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(c.ink, 4.dp.toPx(), pts.last())
        }
        Row(Modifier.fillMaxWidth()) {
            val ago = when (weeksAgo) {
                0L -> "this week"
                1L -> "1 week ago"
                else -> "$weeksAgo weeks ago"
            }
            val last = points.last().startedAt.day(clock)
            val lastLabel = if (!last.isBefore(LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) "This week"
            else last.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            Text("${WorkoutFormat.weight(min)}, $ago", style = Commit.type.micro, color = c.muted, modifier = Modifier.weight(1f))
            Text(lastLabel, style = Commit.type.micro, color = c.muted)
        }
    }
}

// ---- Helpers ----

private fun Long.day(clock: Clock): LocalDate = Instant.ofEpochMilli(this).atZone(clock.zone).toLocalDate()

private fun time(millis: Long, clock: Clock): String =
    Instant.ofEpochMilli(millis).atZone(clock.zone).format(DateTimeFormatter.ofPattern("HH:mm"))

/** "Today", "Yesterday", "Thursday 20 November". */
private fun dayHeading(day: LocalDate, clock: Clock): String {
    val today = LocalDate.now(clock)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE d MMMM" else "EEEE d MMMM yyyy", Locale.getDefault()))
    }
}
