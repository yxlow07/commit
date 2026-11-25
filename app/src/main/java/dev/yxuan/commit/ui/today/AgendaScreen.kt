package dev.yxuan.commit.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.ScreenPadding
import dev.yxuan.commit.ui.format.DueLabels
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import java.time.Clock

/**
 * Agenda (nav: Agenda): every open todo across commitments in due order, under Overdue, Today,
 * Tomorrow, This week, Later and No date. The commitment-by-commitment view stays on Today.
 */
@Composable
fun AgendaScreen(
    onEditTodo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Commit.colors
    val t = Commit.type
    val clock = Clock.systemDefaultZone()
    val open = state.agenda.size
    val late = state.agenda.count { DueLabels.isLate(it.item.dueAt, it.item.hasTime, clock) }
    val summary = when {
        !state.loaded -> ""
        open == 0 -> "Nothing open."
        late == 0 -> "$open open."
        else -> "$open open, $late overdue."
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = ScreenPadding) {
        item(key = "agenda-header") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                Text("Agenda", style = t.titleScreen, color = c.ink, modifier = Modifier.semantics { heading() })
                if (summary.isNotEmpty()) Text(summary, style = t.bodyL, color = c.textStrong)
            }
            Spacer(Modifier.height(Space.l))
        }
        if (state.loaded && open > 0) timeSections(state.agenda, onEditTodo, onToggle = viewModel::setDone)
    }
}
