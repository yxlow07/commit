package dev.yxuan.commit.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
            Modifier.fillMaxWidth().padding(horizontal = Space.screenSide).padding(top = Space.xxl),
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
        return
    }
    val ex = live.exercise ?: return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(ex.name, style = t.bodyL.copy(fontWeight = FontWeight.Bold), color = c.ink, modifier = Modifier.weight(1f))
        Text(WorkoutFormat.position(live), style = t.caption, color = c.muted)
    }
    SetProgress(s.setNo, ex.sets)
    Spacer(Modifier.height(Space.s))
    when (s.phase) {
        WorkoutPhase.READY -> {
            if (live.isTimed) {
                Text("${ex.durationSeconds} s", style = t.displayXl, color = c.ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                PrimaryButton("Start set", onClick = vm::primary, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Stepper(s.reps.toString(), "reps", { vm.stepReps(-1) }, { vm.stepReps(1) }, "Fewer reps", "More reps", Modifier.weight(1f))
                    Stepper(WorkoutFormat.weightValue(s.weight), "kg", { vm.stepWeight(-1) }, { vm.stepWeight(1) }, "Less weight", "More weight", Modifier.weight(1f))
                }
                PrimaryButton("Done set", onClick = vm::primary, modifier = Modifier.fillMaxWidth())
            }
        }
        WorkoutPhase.REST, WorkoutPhase.WORK -> {
            Countdown(s.phaseEndsAt ?: 0)
            val under = if (s.phase == WorkoutPhase.REST) WorkoutFormat.restThen(live)?.let { "Rest, then $it" } ?: "Rest" else "Hold, then rest"
            Text(under, style = t.meta, color = c.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (s.phase == WorkoutPhase.REST) {
                    SecondaryButton("Add 30 s", onClick = vm::addRest, modifier = Modifier.weight(1f))
                    PrimaryButton("Skip rest", onClick = vm::skip, modifier = Modifier.weight(1f))
                } else {
                    PrimaryButton("Done early", onClick = vm::skip, modifier = Modifier.weight(1f))
                }
            }
        }
        WorkoutPhase.COMPLETE -> Unit
    }
    if (live.sets.isNotEmpty()) {
        Text(
            "${live.sets.size} ${if (live.sets.size == 1) "set" else "sets"} done, ${WorkoutFormat.volume(live.volume)}",
            style = t.caption.tnum(),
            color = c.muted,
            modifier = Modifier.padding(top = Space.l),
        )
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
