package dev.yxuan.commit.ui.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import dev.yxuan.commit.data.repo.RecapRepository
import dev.yxuan.commit.domain.Recap
import dev.yxuan.commit.domain.RecapKind
import dev.yxuan.commit.domain.RecapLine
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.RecapRoute
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecapViewModel(savedStateHandle: SavedStateHandle, repo: RecapRepository) : ViewModel() {
    private val route = savedStateHandle.toRoute<RecapRoute>()
    val kind: RecapKind = RecapKind.valueOf(route.kind)
    /** The day under review (from the History grid), or null for today's live recap. */
    val day: LocalDate? = route.epochDay.takeIf { it != RecapRoute.NO_DAY }?.let(LocalDate::ofEpochDay)
    /** Live: rebuilt on every change to todos, completions and workouts, not just when first opened. */
    val recap: StateFlow<Recap?> = repo.observe(kind, day).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * A recap: from the recap notification (F6.4), today's, built live from Room; from the History
 * grid, that day's. Rows are drawn like todo rows: status on the left ("Late" in red, a time, a
 * day), the title, and the commitment underneath tinted toward its colour.
 */
@Composable
fun RecapScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: RecapViewModel = viewModel(factory = AppViewModelFactory)) {
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val c = Commit.colors
    val title = when {
        viewModel.day == LocalDate.now() -> "Today"
        viewModel.day != null -> viewModel.day.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.getDefault()))
        viewModel.kind == RecapKind.MORNING -> "Morning recap"
        else -> "Night recap"
    }
    Column(modifier.fillMaxSize()) {
        PushedTopBar(onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.screenSide, end = Space.screenSide, bottom = Space.xxxl)) {
            item { ScreenTitle(title, Modifier.padding(top = Space.s)) }
            val r = recap ?: return@LazyColumn
            item {
                Text(
                    if (r.isEmpty) "Nothing to report." else "${r.title}.",
                    style = Commit.type.bodyL,
                    color = c.textStrong,
                    modifier = Modifier.padding(top = Space.l, bottom = Space.s),
                )
            }
            r.sections.filter { it.items.isNotEmpty() }.forEach { section ->
                item(key = section.title) {
                    SectionHeading(section.title, Modifier.padding(top = Space.xl), danger = section.title == "Overdue")
                }
                items(section.items) { line -> RecapRow(line) }
            }
        }
    }
}

/** Status in an 88 dp column (fits "11:05 PM"; red when late), title, and a tinted detail line: the todo row without its checkbox. */
@Composable
private fun RecapRow(line: RecapLine) {
    val c = Commit.colors
    val t = Commit.type
    val detailColor = line.color?.let { lerp(c.muted, Color(it), 0.35f) } ?: c.muted
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.rowApp).padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(line.status, line.title, line.detail).joinToString(", ") },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            line.status,
            style = t.body.tnum(),
            color = if (line.late) c.danger else c.muted,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.width(88.dp).padding(top = 1.dp, end = 8.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(line.title, style = t.bodyL, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            line.detail?.let { Text(it, style = t.meta, color = detailColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
