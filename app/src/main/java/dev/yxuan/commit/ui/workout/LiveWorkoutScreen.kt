package dev.yxuan.commit.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.ui.AppViewModelFactory
import dev.yxuan.commit.ui.format.WorkoutFormat
import kotlinx.coroutines.delay

/** The running workout in the app: mirrors the widget's workout tab (F5.2–F5.8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWorkoutScreen(
    onBack: () -> Unit,
    viewModel: LiveWorkoutViewModel = viewModel(factory = AppViewModelFactory),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(live?.routine?.name ?: "Workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val l = live
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            if (l == null) {
                Text("No workout running.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LiveContent(l, viewModel, onFinished = onBack)
            }
        }
    }
}

@Composable
private fun LiveContent(live: LiveWorkout, vm: LiveWorkoutViewModel, onFinished: () -> Unit) {
    val s = live.state
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (s.phase) {
            WorkoutPhase.COMPLETE -> {
                Text("Workout complete", style = MaterialTheme.typography.headlineMedium)
                Text(WorkoutFormat.summary(live), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { vm.end(); onFinished() }) { Text("Done") }
            }

            WorkoutPhase.READY -> {
                Title(live)
                if (live.isTimed) {
                    Text("${live.exercise?.durationSeconds} s", style = MaterialTheme.typography.displayMedium)
                } else {
                    Stepper("Reps", s.reps.toString(), vm::stepReps)
                    Stepper("Weight", WorkoutFormat.weight(s.weight), vm::stepWeight)
                }
                Button(onClick = vm::primary, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (live.isTimed) "Start" else "Done set", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = vm::end) { Text("End workout") }
            }

            WorkoutPhase.REST, WorkoutPhase.WORK -> {
                if (s.phase == WorkoutPhase.REST) {
                    Text("Rest", style = MaterialTheme.typography.headlineSmall)
                    live.upNext?.let { Text("Next: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Title(live)
                }
                Countdown(s.phaseEndsAt ?: 0)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (s.phase == WorkoutPhase.REST) {
                        FilledTonalButton(onClick = vm::addRest) { Text("+30s") }
                        Button(onClick = vm::skip) { Text("Skip rest") }
                    } else {
                        Button(onClick = vm::skip) { Text("Done early") }
                    }
                }
                OutlinedButton(onClick = vm::end) { Text("End workout") }
            }
        }
        if (live.sets.isNotEmpty() && s.phase != WorkoutPhase.COMPLETE) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${live.sets.size} ${if (live.sets.size == 1) "set" else "sets"} done · ${WorkoutFormat.volume(live.volume)} volume",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Title(live: LiveWorkout) {
    Text(live.exercise?.name ?: "", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Text(WorkoutFormat.setProgress(live), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Countdown(endsAt: Long) {
    val remaining by produceState(endsAt - System.currentTimeMillis(), endsAt) {
        while (true) {
            value = endsAt - System.currentTimeMillis()
            delay(200)
        }
    }
    Text(WorkoutFormat.countdown(remaining), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(72.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalIconButton(onClick = { onStep(-1) }) { Icon(Icons.Default.Remove, contentDescription = "Less $label") }
            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            FilledTonalIconButton(onClick = { onStep(1) }) { Icon(Icons.Default.Add, contentDescription = "More $label") }
        }
    }
}
