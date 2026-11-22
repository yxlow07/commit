package dev.yxuan.commit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yxuan.commit.ui.format.ShortDue
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum

// ---- Icons (thin strokes, drawn so they follow the tokens) ----

enum class Glyph { Back, Chevron, Plus, Minus, Tick, Close }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, size: Dp = 20.dp, stroke: Dp = 1.8.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Pair<Float, Float>) = Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w / 24, y * w / 24) else lineTo(x * w / 24, y * w / 24) }
        }
        when (glyph) {
            Glyph.Back -> drawPath(p(15f to 5f, 8f to 12f, 15f to 19f), color, style = s)
            Glyph.Chevron -> drawPath(p(9f to 5f, 16f to 12f, 9f to 19f), color, style = s)
            Glyph.Plus -> {
                drawLine(color, Offset(w / 2, 5 * w / 24), Offset(w / 2, 19 * w / 24), s.width, StrokeCap.Round)
                drawLine(color, Offset(5 * w / 24, w / 2), Offset(19 * w / 24, w / 2), s.width, StrokeCap.Round)
            }
            Glyph.Minus -> drawLine(color, Offset(5 * w / 24, w / 2), Offset(19 * w / 24, w / 2), s.width, StrokeCap.Round)
            Glyph.Tick -> drawPath(p(5f to 12.5f, 9.5f to 17f, 19f to 7f), color, style = s)
            Glyph.Close -> {
                drawLine(color, Offset(6 * w / 24, 6 * w / 24), Offset(18 * w / 24, 18 * w / 24), s.width, StrokeCap.Round)
                drawLine(color, Offset(18 * w / 24, 6 * w / 24), Offset(6 * w / 24, 18 * w / 24), s.width, StrokeCap.Round)
            }
        }
    }
}

// ---- Checkbox ----

/** 22 dp circle with a 1.5 dp `ink` stroke inside a 44 dp hit area; checked: `ink` fill and `onInk` tick. */
@Composable
fun CommitCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, title: String, modifier: Modifier = Modifier) {
    val c = Commit.colors
    Box(
        modifier
            .size(Space.touch)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = (if (checked) "Mark not done: " else "Mark done: ") + title },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) c.ink else Color.Transparent)
                .border(1.5.dp, c.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) GlyphIcon(Glyph.Tick, c.onInk, size = 12.dp, stroke = 2.dp)
        }
    }
}

// ---- Todo row ----

/**
 * The shared todo row: due label in a fixed 56 dp column on the left, title (1 line) and an optional
 * second line in the middle, checkbox on the right. Tapping the title opens the todo.
 */
@Composable
fun TodoRow(
    due: ShortDue,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondLine: String? = null,
    minHeight: Dp = Space.rowApp,
) {
    val c = Commit.colors
    val t = Commit.type
    Row(modifier.fillMaxWidth().heightIn(min = minHeight), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).heightIn(min = minHeight).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                due.text,
                style = t.meta.tnum(),
                color = if (due.late && !checked) c.danger else c.muted,
                maxLines = 1,
                modifier = Modifier.width(Space.dueColumn),
            )
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    style = t.body,
                    color = if (checked) c.muted else c.ink,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!secondLine.isNullOrEmpty()) {
                    Text(secondLine, style = t.meta, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The 44 dp hit area overlaps the row's right padding so the circle lines up with the edge.
        CommitCheckbox(checked, onCheckedChange, title, Modifier.offset(x = 10.dp))
    }
}

// ---- Buttons ----

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Commit.colors
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Commit.shapes.button))
            // Disabled through colour, not Modifier.alpha: an alpha layer that changes inside a
            // dialog window wasn't redrawn, so the button vanished once it became enabled.
            .background(if (enabled) c.ink else lerp(c.surface, c.ink, 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Commit.type.label, color = if (enabled) c.onInk else c.onInk.copy(alpha = 0.7f), maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Commit.colors
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Commit.shapes.button))
            .background(c.fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Commit.type.label, color = if (enabled) c.ink else c.faint, maxLines = 1)
    }
}

/** No background: `ink` Bold, or `muted` Medium for low-priority actions. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, muted: Boolean = false, enabled: Boolean = true, color: Color? = null) {
    val c = Commit.colors
    Box(
        modifier
            .heightIn(min = Space.touch)
            .clip(RoundedCornerShape(Commit.shapes.button))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Commit.type.label.copy(fontWeight = if (muted) FontWeight.Medium else FontWeight.Bold),
            color = if (!enabled) c.faint else color ?: if (muted) c.muted else c.ink,
        )
    }
}

/** 44–48 dp `ink` square (button radius) with a plus. */
@Composable
fun PrimaryRoundButton(onClick: () -> Unit, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val c = Commit.colors
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Commit.shapes.button))
            .background(c.ink)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Plus, c.onInk, size = if (size >= 48.dp) 20.dp else 18.dp, stroke = 2.dp)
    }
}

/** A 44 dp icon button (back arrow, close). */
@Composable
fun IconAction(glyph: Glyph, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Space.touch)
            .clip(RoundedCornerShape(Commit.shapes.button))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Commit.colors.ink, size = 22.dp)
    }
}

// ---- Stepper and set progress ----

@Composable
fun Stepper(
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String,
    plusLabel: String,
    modifier: Modifier = Modifier,
) {
    val c = Commit.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        StepButton(Glyph.Minus, minusLabel, onMinus)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(value, style = Commit.type.body.copy(fontSize = 28.sp, fontWeight = FontWeight.Light, lineHeight = 30.sp).tnum(), color = c.ink)
            Text(unit, style = Commit.type.micro, color = c.muted)
        }
        StepButton(Glyph.Plus, plusLabel, onPlus)
    }
}

@Composable
private fun StepButton(glyph: Glyph, label: String, onClick: () -> Unit) {
    val c = Commit.colors
    Box(
        Modifier
            .size(Space.touch)
            .clip(RoundedCornerShape(Commit.shapes.button))
            .background(c.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, c.ink, size = 22.dp, stroke = 1.6.dp)
    }
}

/** One 4 dp segment per set: done `ink`, current `faint`, future `track`; "Set 3 of 4" beside it. */
@Composable
fun SetProgress(setNo: Int, total: Int, modifier: Modifier = Modifier, label: String = "Set $setNo of $total") {
    val c = Commit.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (n in 1..total.coerceAtLeast(1)) {
                val color = when {
                    n < setNo -> c.ink
                    n == setNo -> c.faint
                    else -> c.track
                }
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
            }
        }
        Text(label, style = Commit.type.meta.tnum(), color = c.muted)
    }
}

// ---- Segmented control, text field, headings, tabs ----

/**
 * Forms only. Track `fill` r14 with 3 dp padding; selected segment `surface`, Bold, soft shadow.
 * Equal-width segments when the labels fit; otherwise ([scrollable]) the track scrolls sideways and
 * each segment is as wide as its label, so long names are never squeezed or cut.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val c = Commit.colors
    val sh = Commit.shapes
    val track = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(sh.segmentTrack))
        .background(c.fill)
        .padding(3.dp)
    if (!scrollable) {
        Row(track.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { option ->
                Segment(label(option), option == selected, { onSelect(option) }, Modifier.weight(1f))
            }
        }
        return
    }
    val listState = rememberLazyListState()
    val selectedIndex = options.indexOf(selected)
    // Bring the selected segment into view (e.g. the widget's current tab far down the list).
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex, scrollOffset = -40)
    }
    LazyRow(track.selectableGroup(), state = listState, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items(options.size) { i ->
            val option = options[i]
            Segment(label(option), option == selected, { onSelect(option) }, Modifier.widthIn(min = 72.dp), padding = 16.dp)
        }
    }
}

@Composable
private fun Segment(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier, padding: Dp = 6.dp) {
    val c = Commit.colors
    val sh = Commit.shapes
    Box(
        modifier
            .height(42.dp)
            .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(sh.segment), ambientColor = c.ink.copy(alpha = 0.12f), spotColor = c.ink.copy(alpha = 0.12f)) else Modifier)
            .clip(RoundedCornerShape(sh.segment))
            .background(if (on) c.surface else Color.Transparent)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Commit.type.label.copy(fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
            color = c.ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = padding),
        )
    }
}

/** Caption label above a `fill` field: no border, 48 dp, r12; focus draws a 2 dp `ink` outline. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Commit.type.caption, color = Commit.colors.muted, modifier = modifier.padding(bottom = 6.dp))
}

@Composable
fun CommitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    textStyle: TextStyle? = null,
) {
    val c = Commit.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        if (label != null) FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = (textStyle ?: Commit.type.body).copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(Commit.shapes.field))
                        .background(c.fill)
                        .then(if (focused) Modifier.border(2.dp, c.ink, RoundedCornerShape(Commit.shapes.field)) else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = Commit.type.body, color = c.muted)
                    inner()
                }
            },
        )
    }
}

/** A field-looking button (date, time) that opens a picker. */
@Composable
fun FieldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, placeholder: Boolean = false) {
    val c = Commit.colors
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Commit.shapes.field))
            .background(c.fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, style = Commit.type.body.tnum(), color = if (placeholder) c.muted else c.ink, maxLines = 1)
    }
}

/** `caption`, `muted`, sentence case; the Overdue heading uses `danger`. 4 dp above the first row. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    Text(
        text,
        style = Commit.type.caption,
        color = if (danger) Commit.colors.danger else Commit.colors.muted,
        modifier = modifier.padding(bottom = Space.xs).semantics { heading() },
    )
}

/** Text tabs: selected Bold `ink`, others Regular `muted`; exposes Tab role and selected state. */
@Composable
fun TextTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = Commit.type.label.copy(fontSize = 16.sp),
    gap: Dp = 22.dp,
) {
    val c = Commit.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .heightIn(min = Space.touch)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = style.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), color = if (on) c.ink else c.muted, maxLines = 1)
            }
        }
    }
}

/** `title.screen` (40 / Bold). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Commit.type.titleScreen, color = Commit.colors.ink, modifier = modifier.semantics { heading() })
}

/** Back arrow on the left, an optional text action on the right (pushed screens). */
@Composable
fun PushedTopBar(onBack: () -> Unit, modifier: Modifier = Modifier, backLabel: String = "Back", action: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = Space.screenSide - 12.dp, end = Space.screenSide - 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(Glyph.Back, backLabel, onBack)
        Spacer(Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A list row that opens something: title, optional detail line, optional trailing value and chevron. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    trailing: String? = null,
    titleStyle: TextStyle = Commit.type.body,
    trailingStyle: TextStyle = Commit.type.meta,
    minHeight: Dp = Space.rowApp,
    chevron: Boolean = true,
    detailColor: Color? = null,
) {
    val c = Commit.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = titleStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrEmpty()) Text(detail, style = Commit.type.meta, color = detailColor ?: c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!trailing.isNullOrEmpty()) {
            Text(trailing, style = trailingStyle.tnum(), color = c.textStrong, modifier = Modifier.padding(start = 12.dp))
        }
        if (chevron) GlyphIcon(Glyph.Chevron, c.faint, size = 16.dp, stroke = 2.dp, modifier = Modifier.padding(start = 10.dp))
    }
}

// ---- Bottom navigation ----

enum class NavTab(val label: String) { Today("Today"), History("History"), Workouts("Workouts"), Settings("Settings") }

/** 72 dp, `surface`, four text items; selected Bold `ink`, others Regular `muted`. No icons. */
@Composable
fun BottomNav(selected: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    val c = Commit.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(72.dp)
            .selectableGroup(),
    ) {
        NavTab.entries.forEach { tab ->
            val on = tab == selected
            Box(
                Modifier.weight(1f).height(72.dp).selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = Commit.type.label.copy(fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                    color = if (on) c.ink else c.muted,
                )
            }
        }
    }
}

/** Standard content padding for app screens. */
val ScreenPadding = PaddingValues(start = Space.screenSide, end = Space.screenSide, top = Space.screenTop, bottom = Space.xxxl)
