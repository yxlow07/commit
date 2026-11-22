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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.repo.Due
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
 * "Repeat every 4 days after I finish it" with the interval on a scroll wheel (F4.2). The editor
 * also offers fixed weekdays (F4.3).
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
    // Remembered while unticked, so ticking again brings back the last interval.
    var days by rememberSaveable { mutableStateOf(repeatDays ?: 4) }
    if (repeatDays != null && repeatDays != days) days = repeatDays
    val weekdays = (recurDays ?: 0) > 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            CommitCheckbox(
                checked = repeating,
                onCheckedChange = { on ->
                    if (on) onRepeatChange(days)
                    else { onRepeatChange(null); onRecurDaysChange(null) }
                },
                title = "Repeat",
                modifier = Modifier.padding(end = 4.dp),
            )
            // The sentence ticks the box too.
            Text(
                if (repeating && weekdays) "Repeat on fixed weekdays"
                else "Repeat every ${if (days == 1) "day" else "$days days"} after I finish it",
                style = Commit.type.body.tnum(),
                color = c.ink,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (repeating) { onRepeatChange(null); onRecurDaysChange(null) } else onRepeatChange(days)
                },
            )
        }
        // The interval on a scroll wheel, once ticked (F4.2).
        if (repeating && !weekdays) {
            // One selection band behind the number and its unit, as on iOS.
            Box(Modifier.padding(start = 44.dp), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.width(150.dp).height(36.dp).background(c.fill, RoundedCornerShape(10.dp)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    WheelPicker(
                        values = REPEAT_DAY_RANGE,
                        value = days,
                        onValueChange = { days = it; onRepeatChange(it) },
                        label = { it.toString() },
                        contentDescription = "Days between repeats",
                        visibleRows = if (compact) 3 else 5,
                        showBand = false,
                    )
                    Text(if (days == 1) "day" else "days", style = Commit.type.body, color = c.ink)
                }
            }
        }
        if (repeating && !compact) {
            TextTabs(
                items = listOf("After I finish it", "On weekdays"),
                selected = if (weekdays) 1 else 0,
                onSelect = { i ->
                    if (i == 0) { onRecurDaysChange(null); onRepeatChange(days) }
                    else { onRepeatChange(null); onRecurDaysChange(recurDays?.takeIf { it > 0 } ?: 1) }
                },
                modifier = Modifier.padding(start = 44.dp),
                style = Commit.type.label,
                gap = 18.dp,
            )
            if (weekdays) WeekdayPicker(recurDays ?: 0, onRecurDaysChange)
        }
    }
}

/** Every day up to a year. */
private val REPEAT_DAY_RANGE = (1..365).toList()

/** Mon…Sun as text toggles: selected Bold `ink`. At least one day stays selected. */
@Composable
private fun WeekdayPicker(mask: Int, onChange: (Int?) -> Unit) {
    val c = Commit.colors
    Row(Modifier.fillMaxWidth().padding(start = 44.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = Weekdays.contains(mask, day)
            val name = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Text(
                name,
                style = Commit.type.label.copy(fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal),
                color = if (on) c.ink else c.muted,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Checkbox) {
                        val next = Weekdays.toggle(mask, day)
                        if (next > 0) onChange(next)
                    }
                    .padding(horizontal = 4.dp, vertical = 12.dp)
                    .semantics { contentDescription = "$name, " + if (on) "selected" else "not selected" },
            )
        }
    }
}
