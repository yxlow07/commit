package dev.yxuan.commit.ui.todos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.repo.Due
import dev.yxuan.commit.ui.format.DueFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Title, commitment and due fields; shared by the todo editor and the widget quick-add popup. */
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
    titleFocusRequester: FocusRequester? = null,
    onDone: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .let { if (titleFocusRequester != null) it.focusRequester(titleFocusRequester) else it },
        )
        CommitmentDropdown(commitments, commitmentId, onCommitmentChange)
        DueFields(due, onDueChange)
        RepeatField(repeatDays, onRepeatChange)
    }
}

/** "Repeat every N days" (F4.1, F4.2). Off = one-off todo. */
@Composable
private fun RepeatField(repeatDays: Int?, onChange: (Int?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(repeatDays?.toString() ?: "7") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Switch(
            checked = repeatDays != null,
            onCheckedChange = { on -> onChange(if (on) text.toIntOrNull()?.coerceAtLeast(1) ?: 1 else null) },
        )
        if (repeatDays == null) {
            Text("Repeat")
        } else {
            Text("Every")
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    text = value.filter(Char::isDigit).take(3)
                    onChange(text.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp),
            )
            Text(if (repeatDays == 1) "day" else "days")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitmentDropdown(
    commitments: List<CommitmentEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = commitments.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Commitment") },
            leadingIcon = selected?.let { { ColorDot(it.color) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            commitments.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    leadingIcon = { ColorDot(c.color) },
                    onClick = {
                        onSelect(c.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun ColorDot(color: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(12.dp).background(Color(color), CircleShape))
}

/** Optional date, then optional time once a date is set (F2.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueFields(due: Due?, onChange: (Due?) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AssistChip(
            onClick = { showDate = true },
            label = { Text(due?.let { DueFormatter.formatDate(it.date) } ?: "Add date") },
            leadingIcon = { Icon(Icons.Default.CalendarToday, null, Modifier.size(AssistChipDefaults.IconSize)) },
        )
        if (due != null) {
            AssistChip(
                onClick = { showTime = true },
                label = { Text(due.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Add time") },
                leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(if (due.time != null) due.copy(time = null) else null) }) {
                    Icon(Icons.Default.Close, contentDescription = if (due.time != null) "Clear time" else "Clear date")
                }
            }
        }
    }

    if (showDate) {
        // DatePicker works in UTC-midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (due?.date ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(Due(date, due?.time))
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime && due != null) {
        val initial = due.time ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(due.copy(time = LocalTime.of(state.hour, state.minute)))
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
