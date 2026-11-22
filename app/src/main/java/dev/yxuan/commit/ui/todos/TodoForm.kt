package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.repo.Due
import dev.yxuan.commit.domain.IntervalUnit
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.components.CommitCheckbox
import dev.yxuan.commit.ui.components.CommitTextField
import dev.yxuan.commit.ui.components.DatePickerSheet
import dev.yxuan.commit.ui.components.FieldButton
import dev.yxuan.commit.ui.components.FieldLabel
import dev.yxuan.commit.ui.components.SegmentedControl
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.components.TextTabs
import dev.yxuan.commit.ui.components.TimePickerSheet
import dev.yxuan.commit.ui.components.WeekdayToggles
import dev.yxuan.commit.ui.components.WheelPicker
import dev.yxuan.commit.ui.format.DueLabels
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.tnum
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Title, commitment, due and repeat fields, shared by the quick-add popup ([compact]: the spec's
 * popup, interval repeat only) and the todo editor (also weekday repeat and reminder lead time).
 */
@Composable
fun TodoForm(
    title: String,
    onTitleChange: (String) -> Unit,
    commitments: List<CommitmentEntity>,
    commitmentId: String?,
    onCommitmentChange: (String) -> Unit,
    due: Due?,
    onDueChange: (Due?) -> Unit,
    repeatDays: Int?,
    onRepeatChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    recurDays: Int? = null,
    onRecurDaysChange: (Int?) -> Unit = {},
    reminderLeadMinutes: Int? = null,
    onReminderLeadChange: (Int?) -> Unit = {},
    compact: Boolean = false,
    titleFocusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CommitTextField(
            value = title,
            onValueChange = onTitleChange,
            label = "Title",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = if (titleFocusRequester != null) Modifier.focusRequester(titleFocusRequester) else Modifier,
        )
        CommitmentField(commitments, commitmentId, onCommitmentChange)
        DueField(due, onDueChange, onReminderLeadChange)
        if (!compact && due?.time != null) LeadField(reminderLeadMinutes, onReminderLeadChange)
        RepeatField(repeatDays, onRepeatChange, recurDays, onRecurDaysChange, compact)
    }
}

/**
 * Segmented control of commitments (workout commitments are never offered). Equal widths while the
 * names fit (up to 4 short names); otherwise it scrolls sideways so every name stays readable.
 */
@Composable
private fun CommitmentField(commitments: List<CommitmentEntity>, selected: String?, onSelect: (String) -> Unit) {
    if (commitments.isEmpty()) return
    // Roughly 9 characters fit in a segment at 4 across a phone-width panel, 13 at 3, 20 at 2.
    val maxChars = when (commitments.size) {
        1, 2 -> 20
        3 -> 13
        4 -> 9
        else -> 0
    }
    val fits = commitments.all { it.name.length <= maxChars }
    Column {
        FieldLabel("Commitment")
        SegmentedControl(
            options = commitments.map { it.id },
            selected = selected,
            onSelect = onSelect,
            label = { id -> commitments.first { it.id == id }.name },
            scrollable = !fits,
        )
    }
}

/** [Fri 28 Nov] [17:00] No date. The time button reads "Add time" until set. */
@Composable
private fun DueField(due: Due?, onChange: (Due?) -> Unit, onLeadChange: (Int?) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Column {
        FieldLabel("Due")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FieldButton(
                text = due?.let { DueLabels.date(it.date) } ?: "Add date",
                placeholder = due == null,
                onClick = { showDate = true },
                modifier = Modifier.weight(1f),
            )
            if (due != null) {
                FieldButton(
                    text = due.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Add time",
                    placeholder = due.time == null,
                    onClick = { showTime = true },
                    modifier = Modifier.width(100.dp),
                )
                TextAction("No date", onClick = { onChange(null); onLeadChange(null) }, muted = true)
            }
        }
    }
    if (showDate) DatePickerSheet(due?.date, { onChange(Due(it, due?.time)) }, { showDate = false })
    if (showTime && due != null) TimePickerSheet(due.time, { onChange(due.copy(time = it)) }, { showTime = false })
}

/** Reminder lead time for todos with a time (F7.1): at the time, or 15/30/60 minutes before. */
@Composable
private fun LeadField(lead: Int?, onChange: (Int?) -> Unit) {
    val options = listOf(0, 15, 30, 60)
    Column {
        FieldLabel("Remind me")
        SegmentedControl(options, lead ?: 0, { onChange(it.takeIf { m -> m > 0 }) }, label = {
            when (it) {
                0 -> "At the time"
                60 -> "1 h before"
                else -> "$it min before"
            }
        })
    }
}

/**
 * Repeat (F4.2, F4.3), laid out like the other fields: a "Repeat" label, a checkbox with a summary
 * ("Repeat every 4 days after I finish it" / "Repeat on Mon, Thu"), then, once ticked, the mode as a
 * segmented control (editor only) and that mode's picker: a scroll wheel of days, or weekday toggles.
 */
@Composable
private fun RepeatField(
    repeatDays: Int?,
    onRepeatChange: (Int?) -> Unit,
    recurDays: Int?,
    onRecurDaysChange: (Int?) -> Unit,
    compact: Boolean,
) {
    val c = Commit.colors
    val repeating = repeatDays != null || (recurDays ?: 0) > 0
    // Nothing picked yet: the summary shows "x". Ticking starts the wheel at 1; the last pick is
    // remembered while unticked.
    var picked by rememberSaveable { mutableStateOf(repeatDays) }
    if (repeatDays != null && repeatDays != picked) picked = repeatDays
    val days = picked ?: 1
    val weekdays = (recurDays ?: 0) > 0
    val toggle = {
        if (repeating) { onRepeatChange(null); onRecurDaysChange(null) } else onRepeatChange(days)
    }

    Column {
        FieldLabel("Repeat")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            CommitCheckbox(checked = repeating, onCheckedChange = { toggle() }, title = "Repeat", modifier = Modifier.offset(x = (-11).dp))
            // The summary ticks the box too. Values are bold; before anything is picked, a muted "x".
            val bold = SpanStyle(fontWeight = FontWeight.Bold, color = if (repeating) c.ink else c.muted)
            val summary = buildAnnotatedString {
                if (repeating && weekdays) {
                    // "Repeat every day", "Repeat on weekdays / weekends", or "Repeat on Mon, Thu".
                    val mask = recurDays ?: 0
                    if (mask == Weekdays.EVERY_DAY) {
                        append("Repeat ")
                        withStyle(bold) { append("every day") }
                    } else {
                        append("Repeat on ")
                        withStyle(bold) {
                            append(if (mask == Weekdays.WEEKDAYS || mask == Weekdays.WEEKENDS) Weekdays.label(mask).lowercase() else Weekdays.label(mask))
                        }
                    }
                } else {
                    append("Repeat every ")
                    val p = picked
                    if (!repeating && p == null) {
                        withStyle(bold) { append("x") }
                        append(" days after I finish it")
                    } else {
                        withStyle(bold) { append(IntervalUnit.describe(if (repeating) days else p!!)) }
                        append(" after I finish it")
                    }
                }
            }
            Text(
                summary,
                style = Commit.type.body.tnum(),
                color = c.ink,
                modifier = Modifier.offset(x = (-11).dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = toggle,
                ),
            )
        }
        if (repeating) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!compact) {
                    SegmentedControl(
                        options = listOf(false, true),
                        selected = weekdays,
                        onSelect = { onWeekdays ->
                            if (!onWeekdays) { onRecurDaysChange(null); onRepeatChange(days) }
                            else { onRepeatChange(null); onRecurDaysChange(recurDays?.takeIf { it > 0 } ?: 1) }
                        },
                        label = { if (it) "On weekdays" else "After I finish it" },
                    )
                }
                if (weekdays) {
                    WeekdayToggles(recurDays ?: 0, { onRecurDaysChange(it) }, keepOne = true)
                } else {
                    // Number and unit wheels under one band, as on iOS. Stored as days (IntervalUnit).
                    val (n, unit) = IntervalUnit.split(days)
                    val numbers = if (unit == IntervalUnit.DAY) DAY_NUMBERS else UNIT_NUMBERS
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(200.dp).height(36.dp).background(c.fill, RoundedCornerShape(10.dp)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            key(unit) {
                                WheelPicker(
                                    values = numbers,
                                    value = n.coerceIn(numbers.first(), numbers.last()),
                                    onValueChange = { v -> (v * unit.days).let { picked = it; onRepeatChange(it) } },
                                    label = { it.toString() },
                                    contentDescription = "Repeat every",
                                    visibleRows = 3,
                                    showBand = false,
                                )
                            }
                            WheelPicker(
                                values = IntervalUnit.entries.map { it.ordinal },
                                value = unit.ordinal,
                                onValueChange = { i ->
                                    val u = IntervalUnit.entries[i]
                                    val keep = n.coerceAtMost(if (u == IntervalUnit.DAY) DAY_NUMBERS.last() else UNIT_NUMBERS.last())
                                    (keep * u.days).let { picked = it; onRepeatChange(it) }
                                },
                                label = { IntervalUnit.entries[it].word(n) },
                                contentDescription = "Unit",
                                visibleRows = 3,
                                showBand = false,
                                width = 104.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Numbers on the repeat wheel: up to a year of days, 99 of the larger units. */
private val DAY_NUMBERS = (1..365).toList()
private val UNIT_NUMBERS = (1..99).toList()
