package dev.yxuan.commit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.yxuan.commit.ui.theme.Commit

/**
 * Drag to reorder rows of a LazyColumn, by a grip handle or by holding the row. [order] is the
 * list as shown: it follows the data ([sync]) except while a drag is in progress, and the new order
 * is handed to the caller when the finger lifts. Rows swap only within their own group.
 */
@Stable
class ReorderState internal constructor(private val listState: LazyListState, private val haptics: HapticFeedback) {
    val order = mutableStateListOf<String>()
    var dragging by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    private var atStart: List<String> = emptyList()
    private var pendingFrom: Int? = null

    /** Follows [ids] from the data, except mid-drag. */
    fun sync(ids: List<String>) {
        if (dragging == null && order.toList() != ids) { order.clear(); order.addAll(ids) }
    }

    internal fun start(id: String) {
        dragging = id
        offset = 0f
        atStart = order.toList()
        pendingFrom = null
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    internal fun dragBy(id: String, dy: Float, group: List<String>) {
        offset += dy
        val visible = listState.layoutInfo.visibleItemsInfo
        val self = visible.firstOrNull { it.key == id } ?: return
        // After a swap, wait until the list has laid the row out in its new place.
        if (pendingFrom != null) { if (self.offset == pendingFrom) return else pendingFrom = null }
        val centre = (self.offset + offset + self.size / 2f).toInt()
        val target = visible.firstOrNull { it.key != id && it.key in group && centre in it.offset until it.offset + it.size } ?: return
        order.add(order.indexOf(target.key as String), order.removeAt(order.indexOf(id)))
        // The row now sits where the target was; keep it under the finger.
        offset -= (target.offset - self.offset)
        pendingFrom = self.offset
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    internal fun end(onDrop: (List<String>) -> Unit) {
        if (dragging != null && order.toList() != atStart) onDrop(order.toList())
        dragging = null
        offset = 0f
    }
}

@Composable
fun rememberReorderState(listState: LazyListState): ReorderState {
    val haptics = LocalHapticFeedback.current
    return remember(listState) { ReorderState(listState, haptics) }
}

/**
 * Drag handlers for one row: [immediate] for the grip (drags at once), otherwise after a hold.
 * They outlive reorders (restarting would drop the drag), so [group] and [onDrop] are read fresh.
 */
fun Modifier.reorderDrag(state: ReorderState, id: String, group: List<String>, onDrop: (List<String>) -> Unit, immediate: Boolean = false): Modifier = composed {
    val currentGroup by rememberUpdatedState(group)
    val currentDrop by rememberUpdatedState(onDrop)
    pointerInput(id, immediate) {
        val onStart: (androidx.compose.ui.geometry.Offset) -> Unit = { state.start(id) }
        val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit =
            { change, amount -> change.consume(); state.dragBy(id, amount.y, currentGroup) }
        if (immediate) {
            detectDragGestures(onDragStart = onStart, onDrag = onDrag, onDragEnd = { state.end(currentDrop) }, onDragCancel = { state.end(currentDrop) })
        } else {
            detectDragGesturesAfterLongPress(onDragStart = onStart, onDrag = onDrag, onDragEnd = { state.end(currentDrop) }, onDragCancel = { state.end(currentDrop) })
        }
    }
}

/** The dragged row: above the others, under the finger, slightly larger, on a surface with a shadow. */
fun Modifier.reorderLift(state: ReorderState, id: String): Modifier = composed {
    val lifted = state.dragging == id
    val shape = RoundedCornerShape(Commit.shapes.button)
    val surface = Commit.colors.surface
    this
        .then(if (lifted) Modifier.zIndex(1f) else Modifier)
        .graphicsLayer {
            translationY = if (lifted) state.offset else 0f
            if (lifted) { scaleX = 1.02f; scaleY = 1.02f }
        }
        .then(if (lifted) Modifier.shadow(8.dp, shape).background(surface, shape) else Modifier)
}

/** The grip handle at the start of a row: a 28 x 44 dp touch area that drags at once. */
@Composable
fun ReorderHandle(state: ReorderState, id: String, group: List<String>, onDrop: (List<String>) -> Unit) {
    Box(
        Modifier.size(width = 28.dp, height = 44.dp).reorderDrag(state, id, group, onDrop, immediate = true),
        contentAlignment = Alignment.CenterStart,
    ) {
        GlyphIcon(Glyph.Grip, Commit.colors.faint, size = 16.dp)
    }
}

/** "Move up" / "Move down" for screen readers, which can't drag; [save] gets the group's new order. */
fun reorderActions(id: String, group: List<String>, save: (List<String>) -> Unit): List<CustomAccessibilityAction> {
    val i = group.indexOf(id)
    fun moved(to: Int) = group.toMutableList().apply { add(to, removeAt(i)) }
    return listOfNotNull(
        if (i > 0) CustomAccessibilityAction("Move up") { save(moved(i - 1)); true } else null,
        if (i in 0 until group.size - 1) CustomAccessibilityAction("Move down") { save(moved(i + 1)); true } else null,
    )
}
