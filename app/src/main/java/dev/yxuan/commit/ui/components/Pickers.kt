package dev.yxuan.commit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.tnum
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Seven weekday toggle buttons in one row: selected `ink` with `onInk` text, others `fill`. Used for
 * a todo's repeat days and a routine's planned days. [keepOne] stops the last day being unselected.
 */
@Composable
fun WeekdayToggles(mask: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, keepOne: Boolean = false) {
    val c = Commit.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            val on = Weekdays.contains(mask, day)
            val name = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(Commit.shapes.button))
                    .background(if (on) c.ink else c.fill)
                    .toggleable(value = on, role = Role.Checkbox) {
                        val next = Weekdays.toggle(mask, day)
                        if (!keepOne || next > 0) onChange(next)
                    }
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    style = Commit.type.label.copy(fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                    color = if (on) c.onInk else c.ink,
                    maxLines = 1,
                )
            }
        }
    }
}

private val HOURS = (0..23).toList()
private val MINUTES = (0..59).toList()

/**
 * Hours and minutes on two scroll wheels under one selection band, like the iOS time picker
 * (24-hour, as the spec's times are).
 */
@Composable
fun TimeWheels(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Commit.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(184.dp).height(36.dp).background(c.fill, RoundedCornerShape(10.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelPicker(
                values = HOURS, value = hour, onValueChange = { onChange(it, minute) },
                label = { "%02d".format(it) }, contentDescription = "Hour", showBand = false,
            )
            Text(":", style = Commit.type.body.copy(fontSize = 22.sp).tnum(), color = c.ink)
            WheelPicker(
                values = MINUTES, value = minute, onValueChange = { onChange(hour, it) },
                label = { "%02d".format(it) }, contentDescription = "Minute", showBand = false,
            )
        }
    }
}

/** The time picker dialog: [TimeWheels], then Cancel / Set time. */
@Composable
fun TimePickerSheet(initial: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val start = initial ?: LocalTime.of(9, 0)
    var hour by remember { mutableIntStateOf(start.hour) }
    var minute by remember { mutableIntStateOf(start.minute) }
    CommitDialog(
        title = "Time",
        onDismiss = onDismiss,
        actions = {
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton("Set time", onClick = {
                onPick(LocalTime.of(hour, minute))
                onDismiss()
            })
        },
    ) {
        TimeWheels(hour, minute, { h, m -> hour = h; minute = m })
    }
}

/**
 * Day, month and year on three scroll wheels under one band, like the time picker. The day wheel
 * follows the month's length (31 → 30 → 28/29); the year runs from last year to ten years ahead.
 */
@Composable
fun DateWheels(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Commit.colors
    val thisYear = LocalDate.now().year
    val years = remember(thisYear, date.year) { (minOf(thisYear - 1, date.year)..maxOf(thisYear + 10, date.year)).toList() }
    val monthNames = remember { Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) } }
    fun set(year: Int, month: Int, day: Int) {
        val len = YearMonth.of(year, month).lengthOfMonth()
        onChange(LocalDate.of(year, month, day.coerceAtMost(len)))
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(248.dp).height(36.dp).background(c.fill, RoundedCornerShape(10.dp)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Re-created when the month's length changes, so the wheel's list matches.
            key(date.lengthOfMonth()) {
                WheelPicker(
                    values = (1..date.lengthOfMonth()).toList(), value = date.dayOfMonth,
                    onValueChange = { set(date.year, date.monthValue, it) },
                    label = { it.toString() }, contentDescription = "Day", showBand = false, width = 64.dp,
                )
            }
            WheelPicker(
                values = (1..12).toList(), value = date.monthValue,
                onValueChange = { set(date.year, it, date.dayOfMonth) },
                label = { monthNames[it - 1] }, contentDescription = "Month", showBand = false, width = 84.dp,
            )
            WheelPicker(
                values = years, value = date.year,
                onValueChange = { set(it, date.monthValue, date.dayOfMonth) },
                label = { it.toString() }, contentDescription = "Year", showBand = false, width = 84.dp,
            )
        }
    }
}

/** The date picker dialog: the full date, [DateWheels], then Today / Cancel / Set date. */
@Composable
fun DatePickerSheet(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(initial ?: LocalDate.now()) }
    CommitDialog(
        title = "Date",
        onDismiss = onDismiss,
        actions = {
            TextAction("Today", onClick = { date = LocalDate.now() }, muted = true)
            Spacer(Modifier.weight(1f))
            TextAction("Cancel", onClick = onDismiss)
            PrimaryButton("Set date", onClick = {
                onPick(date)
                onDismiss()
            })
        },
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.getDefault())),
            style = Commit.type.body,
            color = Commit.colors.textStrong,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        DateWheels(date, { date = it })
    }
}
