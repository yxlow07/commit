package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.clickable
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

/** Segmented control of commitments (workout commitments are never offered). Scrolls past four. */
@Composable
private fun CommitmentField(commitments: List<CommitmentEntity>, selected: String?, onSelect: (String) -> Unit) {
    if (commitments.isEmpty()) return
    Column {
        FieldLabel("Commitment")
        if (commitments.size <= 4) {
            SegmentedControl(commitments.map { it.id }, selected, onSelect, label = { id -> commitments.first { it.id == id }.name })
        } else {
            val index = commitments.indexOfFirst { it.id == selected }.coerceAtLeast(0)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextTabs(commitments.map { it.name }, index, { onSelect(commitments[it].id) }, gap = 18.dp)
            }
        }
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
 * "Repeat every [4] days after I finish it" (F4.2). The editor also offers fixed weekdays (F4.3).
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
    var daysText by rememberSaveable { mutableStateOf((repeatDays ?: 4).toString()) }
    val weekdays = (recurDays ?: 0) > 0

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp)) {
            CommitCheckbox(
                checked = repeating,
                onCheckedChange = { on ->
                    if (on) onRepeatChange(daysText.toIntOrNull()?.coerceAtLeast(1) ?: 4)
                    else { onRepeatChange(null); onRecurDaysChange(null) }
                },
                title = "Repeat",
                modifier = Modifier.padding(end = 4.dp),
            )
            if (!repeating || !weekdays) {
                Text("Repeat every ", style = Commit.type.body, color = c.ink)
                // The number is editable once ticked ("tap 4 to change it").
                BasicTextField(
                    value = daysText,
                    onValueChange = { v ->
                        daysText = v.filter(Char::isDigit).take(3)
                        if (repeating) onRepeatChange(daysText.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                    },
                    enabled = repeating,
                    singleLine = true,
                    textStyle = Commit.type.body.tnum().copy(color = c.ink, textDecoration = if (repeating) TextDecoration.Underline else null),
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width((10 * daysText.length.coerceAtLeast(1) + 4).dp).semantics { contentDescription = "Days between repeats" },
                )
                Text(" days after I finish it", style = Commit.type.body, color = c.ink, maxLines = 1)
            } else {
                Text("Repeat on fixed weekdays", style = Commit.type.body, color = c.ink)
            }
        }
        if (repeating && !compact) {
            TextTabs(
                items = listOf("After I finish it", "On weekdays"),
                selected = if (weekdays) 1 else 0,
                onSelect = { i ->
                    if (i == 0) { onRecurDaysChange(null); onRepeatChange(daysText.toIntOrNull()?.coerceAtLeast(1) ?: 4) }
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
