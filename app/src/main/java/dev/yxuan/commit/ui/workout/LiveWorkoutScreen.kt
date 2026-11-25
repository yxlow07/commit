package dev.yxuan.commit.ui.workout

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.yxuan.commit.ui.components.SectionHeading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.components.PrimaryButton
import dev.yxuan.commit.ui.components.PushedTopBar
import dev.yxuan.commit.ui.components.SecondaryButton
import dev.yxuan.commit.ui.components.SetProgress
import dev.yxuan.commit.ui.components.Stepper
import dev.yxuan.commit.ui.components.TextAction
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.theme.Commit
import dev.yxuan.commit.ui.theme.Space
import dev.yxuan.commit.ui.theme.tnum
import kotlinx.coroutines.delay

/** The running workout in the app: the same layout as the widget's workout panel (F5.2–F5.8). */
@Composable
fun LiveWorkoutScreen(
    onBack: () -> Unit,
    viewModel: LiveWorkoutViewModel = viewModel(factory = AppViewModelFactory),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        PushedTopBar(onBack = onBack) {
            if (live != null && live?.state?.phase != WorkoutPhase.COMPLETE) TextAction("End workout", onClick = viewModel::end, muted = true)
        }
        val l = live
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.screenSide).padding(top = Space.xxl, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            if (l == null) {
                Text("No workout running.", style = Commit.type.bodyL, color = Commit.colors.textStrong)
            } else {
                LiveContent(l, viewModel, onFinished = onBack)
            }
        }
    }
}

@Composable
private fun LiveContent(live: LiveWorkout, vm: LiveWorkoutViewModel, onFinished: () -> Unit) {
    val c = Commit.colors
    val t = Commit.type
    val s = live.state
    if (s.phase == WorkoutPhase.COMPLETE) {
        Text("Workout done", style = t.titleScreen, color = c.ink)
        Text(WorkoutFormat.summary(live), style = t.bodyL, color = c.textStrong)
        Spacer(Modifier.height(Space.s))
        PrimaryButton("Done", onClick = { vm.end(); onFinished() }, modifier = Modifier.fillMaxWidth())
        live.exercise?.takeIf { live.canExtraSet }?.let { last ->
            SecondaryButton("One more set of ${last.name}", onClick = vm::extraSet, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    val ex = live.exercise ?: return
    // Time in the current set as the headline, between sets; a rest or timed hold shows its countdown.
    if (s.phase == WorkoutPhase.READY && !live.isTimed) {
        ElapsedText(s.phaseStartedAt, t.displayL.tnum(), c.ink, Modifier.fillMaxWidth().padding(bottom = Space.l))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(ex.name, style = t.bodyL.copy(fontWeight = FontWeight.Bold), color = c.ink, modifier = Modifier.weight(1f))
        Text(WorkoutFormat.position(live), style = t.caption, color = c.muted)
    }
    SetProgress(s.setNo, ex.sets, label = WorkoutFormat.setOf(s.setNo, ex.sets))
    Spacer(Modifier.height(Space.s))
    when (s.phase) {
        WorkoutPhase.READY -> {
            if (live.isTimed) {
                Text("${ex.durationSeconds} s", style = t.displayXl, color = c.ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                PrimaryButton("Start set", onClick = vm::primary, modifier = Modifier.fillMaxWidth())
                SetActions(vm, canEnd = true)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Stepper(s.reps.toString(), "reps", { vm.stepReps(-1) }, { vm.stepReps(1) }, "Fewer reps", "More reps", Modifier.weight(1f))
                    Stepper(WorkoutFormat.weightValue(s.weight), "kg", { vm.stepWeight(-1) }, { vm.stepWeight(1) }, "Less weight", "More weight", Modifier.weight(1f))
                }
                PrimaryButton("Done set", onClick = vm::primary, modifier = Modifier.fillMaxWidth())
                SetActions(vm, canEnd = true)
            }
        }
        WorkoutPhase.REST, WorkoutPhase.WORK -> {
            val endsAt = s.phaseEndsAt ?: 0
            // Ticks so the screen flips to overtime the moment the rest runs out.
            val now by produceState(System.currentTimeMillis(), endsAt) {
                while (true) { value = System.currentTimeMillis(); delay(200) }
            }
            val over = s.phase == WorkoutPhase.REST && now >= endsAt
            if (over) {
                // Overtime: red, counting up from the end of the rest, until Next set.
                ElapsedText(endsAt, t.displayXl.tnum(), c.danger, Modifier.fillMaxWidth(), prefix = "+")
            } else {
                Countdown(endsAt)
            }
            val under = when {
                over -> WorkoutFormat.restThen(live)?.let { "Rest over, next: $it" } ?: "Rest over"
                s.phase == WorkoutPhase.REST -> WorkoutFormat.restThen(live)?.let { "Rest, then $it" } ?: "Rest"
                else -> "Hold, then rest"
            }
            Text(under, style = t.meta, color = if (over) c.danger else c.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (over) {
                    PrimaryButton(live.nextLabel, onClick = vm::skip, modifier = Modifier.weight(1f))
                } else if (s.phase == WorkoutPhase.REST) {
                    SecondaryButton("Add 30 s", onClick = vm::addRest, modifier = Modifier.weight(1f))
                    PrimaryButton("Skip rest", onClick = vm::skip, modifier = Modifier.weight(1f))
                } else {
                    PrimaryButton("Done early", onClick = vm::skip, modifier = Modifier.weight(1f))
                }
            }
            // The exercise's planned sets are done: another set of it, instead of moving on.
            if (live.canExtraSet) {
                SecondaryButton("One more set of ${ex.name}", onClick = vm::extraSet, modifier = Modifier.fillMaxWidth().padding(top = Space.s))
            } else if (s.phase == WorkoutPhase.REST) {
                // Sets of this exercise are still to come: drop them and go to the next exercise.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextAction("End exercise", onClick = vm::endExercise, muted = true)
                }
            }
        }
        WorkoutPhase.COMPLETE -> Unit
    }
    if (live.doneSets.isNotEmpty()) {
        Text(
            "${live.doneSets.size} ${if (live.doneSets.size == 1) "set" else "sets"} done, ${WorkoutFormat.volume(live.volume)}",
            style = t.caption.tnum(),
            color = c.muted,
            modifier = Modifier.padding(top = Space.l),
        )
    }
    if (live.exercises.size > 1) ExerciseChooser(live, onChoose = vm::choose)
}

/** Under "Done set": pass this set over, or drop the rest of the exercise and move on. */
@Composable
private fun SetActions(vm: LiveWorkoutViewModel, canEnd: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextAction("Skip set", onClick = vm::skipSet, muted = true, modifier = Modifier.offset(x = (-12).dp))
        if (canEnd) TextAction("End exercise", onClick = vm::endExercise, muted = true, modifier = Modifier.offset(x = 12.dp))
    }
}

/**
 * Every exercise of the routine with its progress. Between sets or during a rest, tapping one
 * with sets left makes it the next exercise (do them in any order); finished ones say "Done".
 */
@Composable
private fun ExerciseChooser(live: LiveWorkout, onChoose: (Int) -> Unit) {
    val c = Commit.colors
    val t = Commit.type
    val canSwitch = live.state.phase == WorkoutPhase.READY || live.state.phase == WorkoutPhase.REST
    Column(Modifier.fillMaxWidth().padding(top = Space.l)) {
        SectionHeading("Exercises")
        live.exercises.forEachIndexed { i, ex ->
            val done = live.doneOf(i)
            val current = i == live.state.exerciseIndex
            val extra = current && done >= ex.sets && live.state.phase != WorkoutPhase.REST
            val finished = done >= ex.sets && !extra
            val enabled = canSwitch && !finished && !(current && live.state.phase == WorkoutPhase.READY)
            val detail = when {
                extra -> "Now, extra set"
                finished -> "Done"
                current && live.state.phase == WorkoutPhase.REST -> "$done of ${ex.sets} sets, resting"
                current -> "Now, set ${done + 1} of ${ex.sets}"
                done > 0 -> "$done of ${ex.sets} sets"
                else -> "${ex.sets} sets"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(enabled = enabled, onClickLabel = "Do ${ex.name} next") { onChoose(i) }
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        ex.name,
                        style = t.body.copy(fontWeight = if (current) FontWeight.Bold else FontWeight.Normal),
                        color = if (finished) c.muted else c.ink,
                        textDecoration = if (finished) TextDecoration.LineThrough else null,
                    )
                    Text(detail, style = t.meta.tnum(), color = c.muted)
                }
                if (enabled) Text("Do next", style = t.label, color = c.ink)
            }
        }
    }
}

@Composable
private fun Countdown(endsAt: Long) {
    val remaining by produceState(endsAt - System.currentTimeMillis(), endsAt) {
        while (true) {
            value = endsAt - System.currentTimeMillis()
            delay(200)
        }
    }
    Text(
        WorkoutFormat.countdown(remaining),
        style = Commit.type.displayXl,
        color = Commit.colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Time since [startedAt], the start of the current set ("01:05", as the widget's Chronometer shows it), ticking every second. */
@Composable
private fun ElapsedText(startedAt: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier, prefix: String = "") {
    val now by produceState(System.currentTimeMillis(), startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000 - value % 1_000)
        }
    }
    val total = ((now - startedAt) / 1000).coerceAtLeast(0)
    val text = if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60) else "%02d:%02d".format(total / 60, total % 60)
    Text(prefix + text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier.semantics { contentDescription = (if (prefix.isEmpty()) "Time in this set " else "Rest over by ") + text })
}
