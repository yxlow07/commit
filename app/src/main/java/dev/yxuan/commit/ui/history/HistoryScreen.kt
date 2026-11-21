package dev.yxuan.commit.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.dao.CompletionRow
import dev.yxuan.commit.data.db.dao.SessionSummary
import dev.yxuan.commit.domain.CommitmentStats
import dev.yxuan.commit.domain.Stats
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.todos.ColorDot
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** History (F8): tasks timeline + heatmap + stats, workout sessions, exercise progress. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelFactory),
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val completions by viewModel.completions.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val points by viewModel.exercisePoints.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Tasks", "Workouts", "Progress").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> completions?.let { TasksTab(it, viewModel.clock) }
                1 -> sessions?.let { WorkoutsTab(it, onOpenSession) }
                else -> ProgressTab(points)
            }
        }
    }
}

// ---------------- Tasks ----------------

@Composable
private fun TasksTab(rows: List<CompletionRow>, clock: Clock) {
    var commitment by rememberSaveable { mutableStateOf<String?>(null) }
    var day by rememberSaveable { mutableStateOf<String?>(null) } // ISO date
    val zone = clock.zone
    val filtered = rows.filter { r ->
        (commitment == null || r.commitmentId == commitment) &&
            (day == null || r.doneAt.date(clock).toString() == day)
    }
    val stats = remember(rows) { Stats.commitmentStats(rows, clock) }
    val perDay = remember(rows, commitment) { Stats.perDay(rows.filter { commitment == null || it.commitmentId == commitment }, zone) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Heatmap(perDay, today = LocalDate.now(clock), selected = day?.let(LocalDate::parse)) { picked ->
                day = picked?.toString()
            }
        }
        if (stats.isNotEmpty()) {
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = commitment == null, onClick = { commitment = null }, label = { Text("All") })
                    stats.sortedBy { it.name }.forEach { s ->
                        FilterChip(
                            selected = commitment == s.commitmentId,
                            onClick = { commitment = if (commitment == s.commitmentId) null else s.commitmentId },
                            label = { Text(s.name) },
                            leadingIcon = { ColorDot(s.color) },
                        )
                    }
                }
            }
            item {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stats.filter { commitment == null || it.commitmentId == commitment }, key = { it.commitmentId }) { StatsCard(it) }
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    if (rows.isEmpty()) "Completed todos and chores will show up here." else "Nothing completed for this filter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        // Timeline grouped by day (F8.1).
        filtered.groupBy { it.doneAt.date(clock) }.forEach { (date, list) ->
            item(key = "h$date") { DayHeader(date, LocalDate.now(clock)) }
            items(list, key = { it.completionId }) { r ->
                ListItem(
                    leadingContent = { ColorDot(r.commitmentColor) },
                    headlineContent = { Text(r.title) },
                    supportingContent = {
                        val time = Instant.ofEpochMilli(r.doneAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
                        Text(listOfNotNull(r.commitmentName, time, if (r.isRecurring) "chore" else null).joinToString(" · "))
                    },
                )
            }
        }
    }
}

@Composable
private fun StatsCard(s: CommitmentStats) {
    Card(Modifier.widthIn(min = 160.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(s.color)
                Spacer(Modifier.width(8.dp))
                Text(s.name, style = MaterialTheme.typography.titleSmall)
            }
            Text("${s.doneThisWeek} done this week", style = MaterialTheme.typography.bodyMedium)
            if (s.onTime + s.late > 0) {
                Text("${s.onTime} on time · ${s.late} late", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.choreStreaks.take(2).forEach { (title, streak) ->
                Text("$title: $streak in a row", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Calendar heatmap (F8.2): one column per week (Mon–Sun), last [HISTORY_WEEKS] weeks, newest on the
 * right. Tap a day to filter the timeline; tap again to clear.
 */
@Composable
private fun Heatmap(perDay: Map<LocalDate, Int>, today: LocalDate, selected: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    val lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val firstMonday = lastMonday.minusWeeks((HISTORY_WEEKS - 1).toLong())
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.onSurface

    Column(Modifier.padding(16.dp)) {
        Text("Completions", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (w in 0 until HISTORY_WEEKS) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (d in 0 until 7) {
                        val date = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
                        val count = perDay[date] ?: 0
                        val future = date.isAfter(today)
                        val color = when {
                            future -> Color.Transparent
                            count == 0 -> empty
                            else -> primary.copy(alpha = heatAlpha(count))
                        }
                        Box(
                            Modifier.size(13.dp)
                                .background(color, RoundedCornerShape(3.dp))
                                .then(if (date == selected) Modifier.border(1.5.dp, outline, RoundedCornerShape(3.dp)) else Modifier)
                                .semantics { contentDescription = "${date.format(A11Y_DATE)}: $count completed" }
                                .clickable(enabled = !future) { onSelect(if (date == selected) null else date) },
                        )
                    }
                }
            }
        }
        if (selected != null) {
            Text(
                "Showing ${selected.format(DateTimeFormatter.ofPattern("EEE d MMM"))} · tap again to clear",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private val A11Y_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

/** Five steps: 1, 2–3, 4–5, 6–8, 9+. Even one completion must stand out from an empty day. */
private fun heatAlpha(count: Int): Float = when {
    count >= 9 -> 1f
    count >= 6 -> 0.85f
    count >= 4 -> 0.7f
    count >= 2 -> 0.55f
    else -> 0.4f
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

// ---------------- Workouts ----------------

@Composable
private fun WorkoutsTab(sessions: List<SessionSummary>, onOpen: (String) -> Unit) {
    if (sessions.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Finished workouts will show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(sessions, key = { it.session.id }) { s ->
            ListItem(
                modifier = Modifier.clickable { onOpen(s.session.id) },
                headlineContent = { Text(s.routineName) },
                supportingContent = { Text(sessionLine(s)) },
            )
        }
    }
}

/** "Sat 21 Nov · 42 min · 3,450 kg · 12 sets" (F8.4). */
fun sessionLine(s: SessionSummary): String {
    val date = Instant.ofEpochMilli(s.session.startedAt).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE d MMM"))
    val minutes = maxOf(1L, ((s.session.endedAt ?: s.session.startedAt) - s.session.startedAt) / 60_000)
    return "$date · $minutes min · ${WorkoutFormat.volume(s.volume)} · ${s.setCount} sets"
}

private fun Long.date(clock: Clock): LocalDate = Instant.ofEpochMilli(this).atZone(clock.zone).toLocalDate()
