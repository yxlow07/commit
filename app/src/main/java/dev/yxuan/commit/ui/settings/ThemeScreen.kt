package dev.yxuan.commit.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.ThemeEntity
import dev.yxuan.commit.data.repo.ThemeRepository
import dev.yxuan.commit.domain.theme.ActiveTheme
import dev.yxuan.commit.domain.theme.BuiltinThemes
import dev.yxuan.commit.domain.theme.ThemeMode
import dev.yxuan.commit.domain.theme.ThemeParser
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.GlyphIcon
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SecondaryButton
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.CommitColors
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThemeViewModel(private val repo: ThemeRepository) : ViewModel() {
    val activeTheme: StateFlow<ActiveTheme> =
        repo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveTheme.Default)

    val importedThemes: StateFlow<List<ThemeEntity>> =
        repo.observeThemes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set

    var importing by mutableStateOf(false)
        private set

    fun select(id: String?) {
        error = null
        viewModelScope.launch { repo.select(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    /** Reads a picked file off the main thread (capped just past the 32 KB limit) and imports it. */
    fun importFile(resolver: ContentResolver, uri: Uri) = launchImport {
        val text = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { s ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (buffer.size() <= ThemeParser.MAX_BYTES) {
                    val n = s.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, n)
                }
                buffer.toString(Charsets.UTF_8.name())
            } ?: throw IllegalStateException("Couldn't open that file.")
        }
        repo.import(text)
    }

    fun importUrl(url: String) = launchImport { repo.importFromUrl(url) }

    private fun launchImport(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            importing = true
            error = null
            val result = try { block() } catch (e: Exception) { Result.failure<Unit>(e) }
            result.onFailure { error = it.message ?: "Couldn't import that theme." }
            importing = false
        }
    }
}

/** Themes (nav: Settings): a preview drawn in the theme, the theme list, and import (F9.3). */
@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val active by viewModel.activeTheme.collectAsStateWithLifecycle()
    val imported by viewModel.importedThemes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var linkDialog by remember { mutableStateOf(false) }
    val c = Commit.colors

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importFile(context.contentResolver, it) }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PushedTopBar(onBack = onBack, backLabel = "Back to Settings")
        Column(
            Modifier.padding(start = Space.screenSide, end = Space.screenSide, top = Space.s, bottom = Space.xxxl),
            verticalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            ScreenTitle("Themes")
            Preview(active)

            val options = BuiltinThemes.all.map { Triple(it.id, it, if (it.id == BuiltinThemes.AUTOMATIC) "Default, follows the system" else "Built in") } +
                imported.mapNotNull { t -> ThemeParser.parseOrNull(t.json)?.let { Triple(t.id, ActiveTheme(t.id, it, ThemeMode.SYSTEM), "Imported") } }
            Column(Modifier.selectableGroup()) {
                options.forEach { (id, theme, source) ->
                    ThemeRow(
                        theme = theme,
                        source = source,
                        selected = active.id == id,
                        onSelect = { viewModel.select(id) },
                        onRemove = if (source == "Imported") ({ viewModel.delete(id) }) else null,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    PrimaryButton("Import file", onClick = { filePicker.launch("application/json") }, enabled = !viewModel.importing)
                    SecondaryButton("Import from link", onClick = { linkDialog = true }, enabled = !viewModel.importing)
                }
                Text(
                    "A theme is a JSON file of colours and corner rounding. Missing values fall back to Light.",
                    style = Commit.type.meta,
                    color = c.muted,
                )
                viewModel.error?.let { Text(it, style = Commit.type.meta, color = c.danger) }
            }
        }
    }

    if (linkDialog) {
        var url by remember { mutableStateOf("https://") }
        CommitDialog(
            title = "Import from link",
            onDismiss = { linkDialog = false },
            actions = {
                TextAction("Cancel", onClick = { linkDialog = false })
                PrimaryButton("Import", enabled = url.length > "https://".length, onClick = {
                    viewModel.importUrl(url)
                    linkDialog = false
                })
            },
        ) {
            CommitTextField(url, { url = it.trim() }, label = "Link to a theme file", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        }
    }
}

/** The widget drawn small on the theme's wallpaper colour, in that theme's colours and radius. */
@Composable
private fun Preview(theme: ActiveTheme) {
    val dark = theme.isDark()
    val t = CommitColors.from(if (dark) theme.spec.dark else theme.spec.light, dark)
    val wall = lerp(t.surface, t.ink, 0.15f)
    val radius = (theme.spec.cornerRadius * 0.7f).dp
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Commit.shapes.preview))
            .background(wall)
            .padding(20.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Preview of ${theme.spec.name}" },
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(t.surface).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                Text("Study", style = Commit.type.tabSelected.copy(fontSize = 22.sp), color = t.ink)
                Text("Work", style = Commit.type.label.copy(fontSize = 13.sp), color = t.muted, modifier = Modifier.padding(bottom = 2.dp))
                Text("Home", style = Commit.type.label.copy(fontSize = 13.sp), color = t.muted, modifier = Modifier.padding(bottom = 2.dp))
            }
            PreviewRow("18:00", "Read chapter 4", t, late = false)
            PreviewRow("Late", "Data Structures lab 5", t, late = true)
        }
    }
}

@Composable
private fun PreviewRow(due: String, title: String, t: CommitColors, late: Boolean) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(due, style = Commit.type.caption.copy(fontWeight = FontWeight.Normal), color = if (late) t.danger else t.muted, modifier = Modifier.width(44.dp))
        Text(title, style = Commit.type.meta, color = t.ink, modifier = Modifier.weight(1f), maxLines = 1)
        Box(Modifier.size(16.dp).border(1.5.dp, t.ink, CircleShape))
    }
}

/** 28 dp swatch (surface with a dot of the text colour), name, source; tick when selected. */
@Composable
private fun ThemeRow(theme: ActiveTheme, source: String, selected: Boolean, onSelect: () -> Unit, onRemove: (() -> Unit)?) {
    val c = Commit.colors
    val dark = theme.isDark()
    val t = if (dark) theme.spec.dark else theme.spec.light
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.rowApp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(t.surface)).border(1.dp, c.track, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(t.ink)))
        }
        Column(Modifier.weight(1f)) {
            Text(theme.spec.name, style = Commit.type.body.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = c.ink)
            Text(source, style = Commit.type.meta, color = c.muted)
        }
        if (onRemove != null && !selected) TextAction("Remove", onClick = onRemove, muted = true)
        if (selected) GlyphIcon(Glyph.Tick, c.ink, size = 18.dp, stroke = 2.2.dp)
        Spacer(Modifier.width(0.dp))
    }
}
