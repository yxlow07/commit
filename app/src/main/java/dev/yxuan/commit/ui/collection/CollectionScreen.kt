package dev.yxuan.commit.ui.collection

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.yxuan.commit.data.db.entity.CollectionGroupEntity
import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import dev.yxuan.commit.data.db.entity.CollectionSort
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.photo.CollectionPhotos
import dev.yxuan.commit.data.repo.CollectionRepository
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.ItemInput
import dev.yxuan.commit.domain.CollectionSorting
import dev.yxuan.commit.domain.CollectionZip
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.CollectionRoute
import dev.yxuan.commit.ui.commitments.CommitSwitch
import dev.yxuan.commit.ui.commitments.CommitmentDialog
import dev.yxuan.commit.ui.components.CommitDialog
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.DeleteIconButton
import dev.yxuan.commit.ui.components.FieldLabel
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.GlyphIcon
import dev.yxuan.commit.ui.components.IconAction
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.ScreenTitle
import dev.yxuan.commit.ui.components.SecondaryButton
import dev.yxuan.commit.ui.components.SegmentedControl
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CollectionState(
    val commitment: CommitmentEntity?,
    val groups: List<CollectionGroupEntity>,
    /** Items by group id, each list already in its group's sort order. */
    val items: Map<String, List<CollectionItemEntity>>,
    val photoVersion: Int,
    val loaded: Boolean,
) {
    val total: Int get() = items.values.sumOf { it.size }
    val collected: Int get() = items.values.sumOf { list -> list.count { it.collected } }
}

class CollectionViewModel(
    savedStateHandle: SavedStateHandle,
    private val commitments: CommitmentRepository,
    private val repo: CollectionRepository,
    val photos: CollectionPhotos,
) : ViewModel() {
    val id: String = savedStateHandle.toRoute<CollectionRoute>().id

    val state: StateFlow<CollectionState> = combine(
        commitments.observe(id), repo.observeGroups(id), repo.observeItems(id), photos.version,
    ) { commitment, groups, items, version ->
        val byGroup = items.groupBy { it.groupId }
        CollectionState(
            commitment, groups,
            groups.associate { g -> g.id to CollectionSorting.sorted(byGroup[g.id].orEmpty(), g.sortMode) },
            version, loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionState(null, emptyList(), emptyMap(), 0, loaded = false))

    fun toggle(itemId: String) = launch { repo.toggle(itemId) }
    fun saveItem(itemId: String?, input: ItemInput) = launch { repo.saveItem(itemId, input) }
    fun deleteItem(itemId: String) = launch { repo.deleteItem(itemId) }
    fun createGroup(name: String) = launch { repo.createGroup(id, name) }
    fun renameGroup(groupId: String, name: String) = launch { repo.renameGroup(groupId, name) }
    fun setSort(groupId: String, sort: CollectionSort) = launch { repo.setSort(groupId, sort) }
    fun deleteGroup(groupId: String) = launch { repo.deleteGroup(groupId) }
    fun archive() = launch { commitments.archive(id) }
    fun updateCommitment(name: String, color: Int) = launch { commitments.update(id, name, color, showInWidget = false) }

    /** Moves a group one place up (-1) or down (+1). */
    fun moveGroup(groupId: String, delta: Int) {
        val ids = state.value.groups.map { it.id }.toMutableList()
        val i = ids.indexOf(groupId)
        val j = i + delta
        if (i < 0 || j !in ids.indices) return
        ids.add(j, ids.removeAt(i))
        launch { repo.reorderGroups(id, ids) }
    }

    /** Reads a zip and imports it; [onDone] gets the summary or the reason it failed. */
    fun importZip(resolver: ContentResolver, uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val plan = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { CollectionZip.parse(it) } ?: error("Couldn't open that file")
                }
                if (plan.items.isEmpty()) error("No items found. Check the zip follows the format.")
                repo.import(id, plan).message + "."
            }.getOrElse { "Couldn't import: ${it.message ?: "not a valid zip"}" }
            onDone(message)
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/**
 * A collection (commitment type COLLECTION): how much is collected, then each group as a grid of
 * cards. Tap a card to mark it collected or not, hold it to edit. Missing items are faded so the
 * gaps stand out. Groups sort on their own (the sort menu in each group's header).
 */
@Composable
fun CollectionScreen(onBack: () -> Unit, viewModel: CollectionViewModel = viewModel(factory = AppViewModelFactory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Commit.colors
    val t = Commit.type
    val context = LocalContext.current
    var editing by remember { mutableStateOf<CollectionItemEntity?>(null) }
    var adding by remember { mutableStateOf<String?>(null) } // group id
    var naming by remember { mutableStateOf<CollectionGroupEntity?>(null) }
    var newGroup by rememberSaveable { mutableStateOf(false) }
    var editingCommitment by remember { mutableStateOf(false) }
    var importHelp by rememberSaveable { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val accent = state.commitment?.let { Color(it.color) } ?: c.ink
    // Folded groups are a per-device view preference, so they live in SharedPreferences, not the synced rows.
    val foldPrefs = remember { context.getSharedPreferences("collection_ui", Context.MODE_PRIVATE) }
    var folded by remember { mutableStateOf(foldPrefs.getStringSet("folded", null)?.toSet() ?: emptySet()) }
    fun setFolded(ids: Set<String>) {
        folded = ids
        foldPrefs.edit().putStringSet("folded", ids).apply()
    }
    val groupIds = state.groups.map { it.id }
    val allFolded = groupIds.isNotEmpty() && groupIds.all { it in folded }

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            viewModel.importZip(context.contentResolver, uri) { importing = false; importMessage = it }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PushedTopBar(onBack = onBack) {
            if (groupIds.size > 1) {
                TextAction(if (allFolded) "Expand all" else "Collapse all", muted = true, onClick = {
                    setFolded(if (allFolded) folded - groupIds.toSet() else folded + groupIds)
                })
            }
            TextAction(if (importing) "Importing…" else "Import zip", onClick = { importHelp = true }, muted = true, enabled = !importing)
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Cards about 160 dp wide: 2 across on a phone, more on wider screens.
            val columns = ((maxWidth - Space.screenSide * 2) / 160.dp).toInt().coerceIn(2, 6)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.screenSide, end = Space.screenSide, top = Space.s, bottom = Space.xxxl)) {
                item(key = "header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScreenTitle(state.commitment?.name ?: "", Modifier.weight(1f, fill = false))
                        IconAction(Glyph.Pencil, "Edit ${state.commitment?.name ?: "collection"}", { editingCommitment = true }, tint = c.muted, glyphSize = 18.dp)
                    }
                    if (state.loaded) {
                        Text(
                            if (state.total == 0) "Nothing in it yet." else "${state.collected} of ${state.total} collected.",
                            style = t.bodyL.tnum(), color = c.textStrong, modifier = Modifier.padding(top = Space.l),
                        )
                        if (state.total > 0) ProgressBar(state.collected.toFloat() / state.total, accent, Modifier.padding(top = Space.m))
                    }
                    Spacer(Modifier.height(Space.xxl))
                }
                state.groups.forEachIndexed { gi, group ->
                    val items = state.items[group.id].orEmpty()
                    val isFolded = group.id in folded
                    item(key = "g-${group.id}") {
                        GroupHeader(
                            group = group, collected = items.count { it.collected }, total = items.size,
                            folded = isFolded,
                            onFold = { setFolded(if (isFolded) folded - group.id else folded + group.id) },
                            first = gi == 0, last = gi == state.groups.lastIndex,
                            onAdd = { adding = group.id; if (isFolded) setFolded(folded - group.id) },
                            onSort = { viewModel.setSort(group.id, it) },
                            onRename = { naming = group },
                            onMove = { viewModel.moveGroup(group.id, it) },
                            onDelete = { viewModel.deleteGroup(group.id) },
                            // Tighter under a folded group, so a stack of folded groups reads as a list.
                            modifier = Modifier.padding(top = when {
                                gi == 0 -> 0.dp
                                state.groups[gi - 1].id in folded -> Space.s
                                else -> Space.xxl
                            }),
                        )
                    }
                    if (isFolded) return@forEachIndexed
                    if (items.isEmpty()) {
                        item(key = "ge-${group.id}") {
                            Text("Nothing here yet. Add an item with +.", style = t.body, color = c.muted, modifier = Modifier.padding(vertical = Space.s))
                        }
                    }
                    items(items.chunked(columns), key = { row -> "r-" + row.first().id }) { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            row.forEach { item ->
                                ItemCard(
                                    item = item, file = viewModel.photos.fileFor(item), photoVersion = state.photoVersion,
                                    onToggle = { viewModel.toggle(item.id) }, onEdit = { editing = item },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (state.loaded) {
                    item(key = "new-group") {
                        TextAction("New group", onClick = { newGroup = true }, modifier = Modifier.padding(top = Space.l).offset(x = (-12).dp))
                    }
                }
            }
        }
    }

    if (importHelp) ImportHelpDialog(onDismiss = { importHelp = false }, onChoose = {
        importHelp = false
        pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
    })
    importMessage?.let { msg ->
        CommitDialog(title = "Import", onDismiss = { importMessage = null }, actions = { PrimaryButton("Done", onClick = { importMessage = null }) }) {
            Text(msg, style = t.body, color = c.textStrong)
        }
    }
    if (newGroup) NameDialog("New group", "", "Add group", onDismiss = { newGroup = false }) { viewModel.createGroup(it); newGroup = false }
    naming?.let { g -> NameDialog("Rename group", g.name, "Save", onDismiss = { naming = null }) { viewModel.renameGroup(g.id, it); naming = null } }
    if (editingCommitment) state.commitment?.let { cm ->
        CommitmentDialog(
            initial = cm,
            onDismiss = { editingCommitment = false },
            onSave = { name, _, color, _ -> viewModel.updateCommitment(name, color); editingCommitment = false },
            onArchive = { viewModel.archive(); editingCommitment = false; onBack() },
        )
    }
    val editorGroup = editing?.groupId ?: adding
    if (editorGroup != null) {
        ItemDialog(
            initial = editing,
            groups = state.groups,
            groupId = editorGroup,
            photo = editing?.let { viewModel.photos.fileFor(it) },
            cameraFile = { viewModel.photos.cameraFile() },
            onDismiss = { editing = null; adding = null },
            onDelete = editing?.let { e -> { viewModel.deleteItem(e.id); editing = null } },
            onSave = { input -> viewModel.saveItem(editing?.id, input); editing = null; adding = null },
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Commit.colors.track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/**
 * A chevron, the group name and "12/20" (tap to fold or unfold the group), then its sort (a menu
 * that also holds rename, move and delete) and +.
 */
@Composable
private fun GroupHeader(
    group: CollectionGroupEntity, collected: Int, total: Int, first: Boolean, last: Boolean, folded: Boolean, onFold: () -> Unit,
    onAdd: () -> Unit, onSort: (CollectionSort) -> Unit, onRename: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Commit.colors
    val t = Commit.type
    var menu by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (folded) 0f else 90f, label = "fold")
    Row(modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = Space.touch)
                .clickable(onClickLabel = if (folded) "Expand ${group.name}" else "Collapse ${group.name}", onClick = onFold)
                .semantics { stateDescription = if (folded) "Collapsed" else "Expanded" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Chevron, c.muted, size = 16.dp, stroke = 2.dp, modifier = Modifier.rotate(turn))
            Spacer(Modifier.width(8.dp))
            Text(group.name, style = t.titleDialog, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  $collected/$total", style = t.meta.tnum(), color = c.muted)
        }
        Box {
            TextAction(CollectionSorting.label(group.sortMode) + " ▾", onClick = { menu = true }, muted = true)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                CollectionSort.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(CollectionSorting.label(s), style = t.body, color = c.ink) },
                        trailingIcon = if (s == group.sortMode) ({ GlyphIcon(Glyph.Tick, c.ink, size = 16.dp) }) else null,
                        onClick = { onSort(s); menu = false },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Rename", style = t.body) }, onClick = { onRename(); menu = false })
                if (!first) DropdownMenuItem(text = { Text("Move up", style = t.body) }, onClick = { onMove(-1); menu = false })
                if (!last) DropdownMenuItem(text = { Text("Move down", style = t.body) }, onClick = { onMove(1); menu = false })
                DropdownMenuItem(text = { Text("Delete group", style = t.body, color = c.danger) }, onClick = { onDelete(); menu = false })
            }
        }
        IconAction(Glyph.Plus, "Add item to ${group.name}", onAdd, tint = c.muted, glyphSize = 18.dp, modifier = Modifier.offset(x = 12.dp))
    }
}

/**
 * A square photo (or the name, when there's no photo) with the name below and a round check in
 * the corner. Missing items are faded. Tap toggles collected; hold edits.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(item: CollectionItemEntity, file: File?, photoVersion: Int, onToggle: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val c = Commit.colors
    val t = Commit.type
    val label = item.name ?: "Item"
    Column(
        // Not clipped to the card's rounded shape: only the photo square is, so the name below keeps its corners.
        modifier
            .combinedClickable(role = Role.Checkbox, onClickLabel = if (item.collected) "Mark missing" else "Mark collected", onLongClickLabel = "Edit", onClick = onToggle, onLongClick = onEdit)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = if (item.collected) "Collected" else "Missing"
            },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(c.fill)) {
            Box(Modifier.fillMaxSize().alpha(if (item.collected) 1f else 0.45f), contentAlignment = Alignment.Center) {
                if (file != null) {
                    // Keyed on the file's modified time, so a replaced photo isn't served from the cache.
                    val context = LocalContext.current
                    val request = remember(file, photoVersion) {
                        ImageRequest.Builder(context).data(file).memoryCacheKey("${file.path}:${file.lastModified()}").build()
                    }
                    // Fit, not crop: the whole can shows, scaled to the square's larger side.
                    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                } else {
                    Text(if (item.hasPhoto) "…" else label, style = t.body, color = c.textStrong, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(Space.m))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape)
                    .background(if (item.collected) c.ink else c.surface.copy(alpha = 0.85f))
                    .then(if (item.collected) Modifier else Modifier.border(1.5.dp, c.ink, CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.collected) GlyphIcon(Glyph.Tick, c.onInk, size = 14.dp)
            }
        }
        // With a photo the name goes underneath; without one it's already in the square.
        if (item.name != null && item.hasPhoto) {
            Text(item.name, style = t.meta, color = if (item.collected) c.ink else c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    CommitDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton(confirm, onClick = { onSave(name.trim()) }, enabled = name.isNotBlank())
        },
    ) {
        CommitTextField(name, { name = it }, label = "Name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    }
}

/** What the zip should look like, then the file picker. */
@Composable
private fun ImportHelpDialog(onDismiss: () -> Unit, onChoose: () -> Unit) {
    val t = Commit.type
    val c = Commit.colors
    CommitDialog(
        title = "Import from a zip",
        onDismiss = onDismiss,
        actions = {
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton("Choose zip", onClick = onChoose)
        },
    ) {
        Text("Folders become groups. Images become items, named after the file.", style = t.body, color = c.textStrong)
        Text("Group name/Item name.jpg", style = t.meta, color = c.muted)
    }
}

/** Add or edit an item: name, photo (camera or gallery), group, collected; delete when editing. */
@Composable
private fun ItemDialog(
    initial: CollectionItemEntity?,
    groups: List<CollectionGroupEntity>,
    groupId: String,
    photo: File?,
    cameraFile: () -> File,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (ItemInput) -> Unit,
) {
    val context = LocalContext.current
    val t = Commit.type
    val c = Commit.colors
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var group by rememberSaveable { mutableStateOf(groupId) }
    var collected by rememberSaveable { mutableStateOf(initial?.collected ?: false) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var removed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { picked = uri; removed = false } }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) { picked = cameraUri; removed = false } }
    val shown: Any? = picked ?: photo.takeIf { !removed }
    val hasPhoto = shown != null

    CommitDialog(
        title = if (initial == null) "New item" else "Edit item",
        onDismiss = onDismiss,
        actions = {
            onDelete?.let {
                DeleteIconButton(onClick = it, contentDescription = "Delete ${initial?.name ?: "item"}")
                Spacer(Modifier.weight(1f))
            }
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton(
                if (initial == null) "Add item" else "Save",
                onClick = { onSave(ItemInput(name, group, collected, picked, removePhoto = removed)) },
                // An item needs a name, a photo, or both.
                enabled = name.isNotBlank() || hasPhoto || (initial?.hasPhoto == true && !removed),
            )
        },
    ) {
        CommitTextField(name, { name = it }, label = "Name", placeholder = "Optional with a photo", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        Column {
            FieldLabel("Photo")
            if (shown != null) {
                AsyncImage(model = shown, contentDescription = "Photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(c.fill))
                Spacer(Modifier.height(Space.s))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton("Camera", onClick = {
                    scope.launch {
                        val file = cameraFile()
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                        cameraUri = uri
                        takePhoto.launch(uri)
                    }
                })
                SecondaryButton("Gallery", onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                if (hasPhoto) TextAction("Remove", onClick = { picked = null; removed = true }, muted = true)
            }
        }
        if (groups.size > 1) {
            Column {
                FieldLabel("Group")
                SegmentedControl(groups.map { it.id }, group, { group = it }, label = { id -> groups.first { it.id == id }.name }, scrollable = true)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Collected", style = t.body, color = c.ink, modifier = Modifier.weight(1f))
            CommitSwitch(collected, { collected = it })
        }
    }
}
