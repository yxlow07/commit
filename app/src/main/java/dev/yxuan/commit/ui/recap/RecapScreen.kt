package dev.yxuan.commit.ui.recap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.RecapRoute
import dev.yxuan.commit.ui.settings.SectionTitle
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(onBack: () -> Unit, viewModel: RecapViewModel = viewModel(factory = AppViewModelFactory)) {
    val recap by viewModel.recap.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.kind == RecapKind.MORNING) "Morning recap" else "Night recap") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val r = recap ?: return@Scaffold
        if (r.isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing to report.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text(r.summary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            r.sections.forEach { section ->
                item(key = section.title) { SectionTitle(section.title) }
                items(section.lines) { line -> ListItem(headlineContent = { Text(line) }) }
            }
        }
    }
}
