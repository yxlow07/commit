package dev.yxuan.commit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * An iOS-style scroll wheel: rows snap to a `fill` selection band in the middle; rows above and
 * below shrink, fade and tilt away like a drum. Each value passing the band gives a light haptic
 * tick. TalkBack reads it as an adjustable value (swipe up/down, or set directly).
 */
@Composable
fun WheelPicker(
    values: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    label: (Int) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    width: Dp = 72.dp,
    rowHeight: Dp = 36.dp,
    visibleRows: Int = 5,
    /** False when the caller draws one band behind the wheel and its unit label, as iOS does. */
    showBand: Boolean = true,
) {
    val c = Commit.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val startIndex = values.indexOf(value).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val fling = rememberSnapFlingBehavior(state)
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val half = visibleRows / 2
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    // The row in the band: the first visible item, or the next one once it's more than half scrolled.
    val centered by remember {
        derivedStateOf {
            val i = state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0
            i.coerceIn(0, values.lastIndex)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().collect { i ->
            if (values[i] != currentValue) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                currentOnChange(values[i])
            }
        }
    }
    // Follow outside changes (e.g. another field resets the value) when the wheel is at rest.
    LaunchedEffect(value) {
        val target = values.indexOf(value)
        if (target >= 0 && target != centered && !state.isScrollInProgress) state.scrollToItem(target)
    }

    Box(
        modifier
            .width(width)
            .height(rowHeight * visibleRows)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = label(values[centered])
                progressBarRangeInfo = ProgressBarRangeInfo(centered.toFloat(), 0f..values.lastIndex.toFloat(), steps = values.size - 2)
                setProgress { target ->
                    val i = target.roundToInt().coerceIn(0, values.lastIndex)
                    scope.launch { state.scrollToItem(i) }
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Selection band behind the middle row.
        if (showBand) Box(Modifier.fillMaxWidth().height(rowHeight).background(c.fill, RoundedCornerShape(10.dp)))
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = rowHeight * half),
            modifier = Modifier.fillMaxWidth().height(rowHeight * visibleRows),
        ) {
            items(values.size) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .graphicsLayer {
                            // Read during drawing, so scrolling redraws rows without recomposing them.
                            // Item offsets start after the top padding, which is where the band is.
                            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                            val distance = info?.let { it.offset / rowPx } ?: (half + 1f)
                            val d = abs(distance).coerceAtMost(half + 1f)
                            // Drum: tilt away and shrink with distance; fade toward the edges.
                            rotationX = (-distance * 22f).coerceIn(-70f, 70f)
                            val scale = 1f - 0.08f * d
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - 0.28f * d).coerceIn(0.15f, 1f)
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { state.animateScrollToItem(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val on = i == centered
                    Text(
                        label(values[i]),
                        style = Commit.type.body.copy(fontSize = 22.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal).tnum(),
                        color = if (on) c.ink else c.muted,
                    )
                }
            }
        }
    }
}
