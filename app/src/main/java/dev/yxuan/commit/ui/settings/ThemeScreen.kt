package dev.yxuan.commit.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.ThemeEntity
import dev.yxuan.commit.data.repo.ThemeRepository
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.BuiltinThemes
import dev.yxuan.commit.domain.theme.ThemeColors
import dev.yxuan.commit.domain.theme.ThemeDefaults
import dev.yxuan.commit.domain.theme.ThemeMode
import dev.yxuan.commit.domain.theme.ThemeParser
import dev.yxuan.commit.domain.theme.ThemeSpec
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.theme.CommitTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val repo: ThemeRepository) : ViewModel() {
    val activeTheme: StateFlow<ActiveTheme> =
        repo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveTheme.Dynamic)

    val importedThemes: StateFlow<List<ThemeEntity>> =
        repo.observeThemes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    var importing by mutableStateOf(false)
        private set

    fun clearError() { error = null }

    fun select(id: String?) {
        viewModelScope.launch {
            repo.select(id)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id)
        }
    }

    fun importJson(json: String) {
        viewModelScope.launch {
            importing = true
            error = null
            val result = repo.import(json)
            result.onFailure {
                error = it.message
            }
            importing = false
        }
    }

    fun importUrl(url: String) {
        viewModelScope.launch {
            importing = true
            error = null
            val result = repo.importFromUrl(url)
            result.onFailure {
                error = it.message
            }
            importing = false
        }
    }
}

/** Theme picker with previews, import from file and link (F9.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
    val importedThemes by viewModel.importedThemes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.use { s ->
                    s.bufferedReader().readText()
                }
                if (content != null) {
                    viewModel.importJson(content)
                }
            } catch (e: Exception) {
                // error will be handled
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (viewModel.importing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            SectionTitle("Built-in themes")
            ThemeCard(
                title = "Material You",
                subtitle = "Wallpaper colours (Android 12+)",
                isSelected = activeTheme is ActiveTheme.Dynamic,
                onClick = { viewModel.select(BuiltinThemes.DYNAMIC) },
            )

            ThemeCard(
                title = "Light",
                subtitle = "Commit palette · forced light",
                isSelected = activeTheme.id == BuiltinThemes.LIGHT,
                onClick = { viewModel.select(BuiltinThemes.LIGHT) },
                previewSpec = ThemeDefaults.spec("Light"),
                previewMode = ThemeMode.LIGHT,
            )

            ThemeCard(
                title = "Dark",
                subtitle = "Commit palette · forced dark",
                isSelected = activeTheme.id == BuiltinThemes.DARK,
                onClick = { viewModel.select(BuiltinThemes.DARK) },
                previewSpec = ThemeDefaults.spec("Dark"),
                previewMode = ThemeMode.DARK,
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Imported themes")
            if (importedThemes.isEmpty()) {
                Text(
                    "No imported themes yet. Import from a JSON file or link below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                importedThemes.forEach { entity ->
                    val spec = remember(entity.json) { ThemeParser.parseOrNull(entity.json) }
                    ThemeCard(
                        title = entity.name,
                        subtitle = spec?.let { "${it.font.key.replaceFirstChar { c -> c.uppercase() }} · ${it.cornerRadius.toInt()} dp" } ?: "Invalid",
                        isSelected = activeTheme.id == entity.id,
                        onClick = { viewModel.select(entity.id) },
                        onDelete = { viewModel.delete(entity.id) },
                        previewSpec = spec,
                        previewMode = ThemeMode.SYSTEM,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { filePicker.launch("application/json") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import file")
                }
                Button(
                    onClick = { showUrlDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import link")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUrlDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Import theme from link") },
            text = {
                Column {
                    Text(
                        "Paste a link to a theme JSON file (https://):",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = { Text("https://example.com/theme.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUrlDialog = false
                        viewModel.importUrl(url)
                    },
                    enabled = url.isNotBlank(),
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    viewModel.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Theme import failed") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun ThemeCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    previewSpec: ThemeSpec? = null,
    previewMode: ThemeMode = ThemeMode.SYSTEM,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete theme", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (previewSpec != null) {
                Spacer(Modifier.height(8.dp))
                ThemeMiniPreview(spec = previewSpec, mode = previewMode)
            }
        }
    }
}

@Composable
private fun ThemeMiniPreview(spec: ThemeSpec, mode: ThemeMode) {
    CommitTheme(active = ActiveTheme.Fixed(spec.name, spec, mode)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Color swatches
                ColorSwatch(Color(spec.light.primary))
                ColorSwatch(Color(spec.light.secondary))
                ColorSwatch(Color(spec.light.surfaceVariant))

                Spacer(Modifier.weight(1f))

                // Interactive elements sample
                Button(
                    onClick = {},
                    modifier = Modifier.height(28.dp),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text("Button", style = MaterialTheme.typography.labelSmall)
                }

                Checkbox(
                    checked = true,
                    onCheckedChange = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(16.dp),
    ) {}
}
