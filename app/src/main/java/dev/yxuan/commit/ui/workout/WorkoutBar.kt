package dev.yxuan.commit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.Glyph
import dev.yxuan.commit.ui.components.GlyphIcon
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.delay

/**
 * The running workout, pinned above the bottom nav on Today, History and Settings, so it is always
 * one tap away (like a music player's mini bar). Dark like the workout notification.
 */
@Composable
fun LiveWorkoutBar(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveWorkoutViewModel = viewModel(key = "workout-bar", factory = AppViewModelFactory),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val l = live ?: return
    if (l.state.phase == WorkoutPhase.COMPLETE) {
        // Finished (on the widget, the notification or before leaving the workout screen): the
        // session is already saved, so close it here rather than asking for a "Done" tap.
        LaunchedEffect(l.state.sessionId) { viewModel.end() }
        return
    }
    val c = Commit.colors
    val t = Commit.type
    val line = barLine(l)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clip(RoundedCornerShape(Commit.shapes.button))
            .background(c.ink)
            .clickable(role = Role.Button, onClickLabel = "Open workout", onClick = onOpen)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.l, vertical = Space.s)
            .semantics(mergeDescendants = true) { contentDescription = "Workout in progress: ${l.routine.name}, ${line.text}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(l.routine.name, style = t.label.copy(fontWeight = FontWeight.Bold), color = c.onInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Overtime in bold rather than red: the theme's red is too dark to read on the ink bar.
            Text(
                line.text,
                style = t.meta.tnum().copy(fontWeight = if (line.over) FontWeight.Bold else FontWeight.Normal),
                color = if (line.over) c.onInk else c.onInk.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        GlyphIcon(Glyph.Chevron, c.onInk.copy(alpha = 0.72f), size = 16.dp)
    }
}

private data class BarLine(val text: String, val over: Boolean = false)

/** "Bench, set 2 of 3", "Resting 0:42, then set 3", "Rest over +0:12". */
@Composable
private fun barLine(l: LiveWorkout): BarLine {
    val s = l.state
    val ticking = s.phase == WorkoutPhase.REST || s.phase == WorkoutPhase.WORK
    val now by produceState(System.currentTimeMillis(), ticking, s.phaseEndsAt) {
        while (ticking) { value = System.currentTimeMillis(); delay(500) }
    }
    val ex = l.exercise
    return when (s.phase) {
        WorkoutPhase.COMPLETE -> BarLine("Workout done")
        WorkoutPhase.READY -> BarLine(if (ex != null) "${ex.name}, ${WorkoutFormat.setOf(s.setNo, ex.sets).lowercase()}" else "")
        WorkoutPhase.WORK -> BarLine("${ex?.name ?: "Hold"} ${WorkoutFormat.countdown((s.phaseEndsAt ?: now) - now)}")
        WorkoutPhase.REST -> {
            val endsAt = s.phaseEndsAt ?: now
            if (now >= endsAt) {
                val over = (now - endsAt) / 1000
                BarLine("Rest over +%d:%02d".format(over / 60, over % 60), over = true)
            } else {
                val then = WorkoutFormat.restThen(l)?.let { ", then $it" }.orEmpty()
                BarLine("Resting ${WorkoutFormat.countdown(endsAt - now)}$then")
            }
        }
    }
}
