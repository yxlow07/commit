package dev.yxuan.commit.ui.recap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import dev.yxuan.commit.data.repo.RecapRepository
import dev.yxuan.commit.domain.Recap
import dev.yxuan.commit.domain.RecapKind
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.RecapRoute
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SectionHeading
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecapViewModel(savedStateHandle: SavedStateHandle, repo: RecapRepository) : ViewModel() {
    val kind: RecapKind = RecapKind.valueOf(savedStateHandle.toRoute<RecapRoute>().kind)
    private val _recap = MutableStateFlow<Recap?>(null)
    val recap: StateFlow<Recap?> = _recap

    init {
        viewModelScope.launch { _recap.value = repo.build(kind) }
    }
}

/** Full recap, opened from the recap notification (F6.4). Built live from Room. */
@Composable
fun RecapScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: RecapViewModel = viewModel(factory = AppViewModelFactory)) {
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    val c = Commit.colors
    Column(modifier.fillMaxSize()) {
    PushedTopBar(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.screenSide, end = Space.screenSide, bottom = Space.xxxl)) {
        item {
            ScreenTitle(if (viewModel.kind == RecapKind.MORNING) "Morning recap" else "Night recap", Modifier.padding(top = Space.s))
        }
        val r = recap ?: return@LazyColumn
        item {
            Text(
                if (r.isEmpty) "Nothing to report." else r.title,
                style = Commit.type.bodyL,
                color = c.textStrong,
                modifier = Modifier.padding(top = Space.l, bottom = Space.s),
            )
        }
        r.sections.filter { it.lines.isNotEmpty() }.forEach { section ->
            item(key = section.title) { SectionHeading(section.title, Modifier.padding(top = Space.l), danger = section.title == "Overdue") }
            items(section.lines) { line ->
                Text(line, style = Commit.type.body, color = c.ink, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 10.dp))
            }
        }
    }
}
}
