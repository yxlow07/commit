package dev.yxuan.commit.widget

import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.yxuan.commit.R
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.ui.format.WorkoutFormat

/**
 * Workout tab (F5.2): the routine picker when idle, otherwise the current step of the
 * running workout. All state comes from Room; the countdown is a native Chronometer.
 */
@Composable
fun WorkoutTab(routines: List<RoutineSummary>, live: LiveWorkout?) {
    when {
        live == null -> RoutinePicker(routines, runningElsewhere = false)
        routines.none { it.routine.id == live.routine.id } -> RoutinePicker(routines, runningElsewhere = true)
        else -> LiveStep(live)
    }
}

@Composable
private fun RoutinePicker(routines: List<RoutineSummary>, runningElsewhere: Boolean) {
    if (routines.isEmpty()) {
        Centered("Add a routine in the app to start a workout")
        return
    }
    Column(GlanceModifier.fillMaxSize()) {
        if (runningElsewhere) Caption("Another workout is running; starting one ends it.")
        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(routines, itemId = { it.routine.id.hashCode().toLong() }) { r ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.routine.name, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Medium))
                        Caption(if (r.exerciseCount == 1) "1 exercise" else "${r.exerciseCount} exercises")
                    }
                    if (r.exerciseCount > 0) {
                        Pill("Start", actionRunCallback<StartWorkoutAction>(actionParametersOf(RoutineIdParam to r.routine.id)), primary = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStep(live: LiveWorkout) {
    val s = live.state
    when (s.phase) {
        WorkoutPhase.COMPLETE -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Text("Workout complete", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Caption(WorkoutFormat.summary(live))
            Spacer(GlanceModifier.height(8.dp))
            Pill("Done", actionRunCallback<EndWorkoutAction>(), primary = true)
        }

        WorkoutPhase.READY -> Column(GlanceModifier.fillMaxSize()) {
            Header(live)
            Spacer(GlanceModifier.height(6.dp))
            if (live.isTimed) {
                Text("${live.exercise?.durationSeconds} s", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stepper("${s.reps}", "reps", StepRepsAction::class.java, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    Stepper(WorkoutFormat.weight(s.weight), "", StepWeightAction::class.java, GlanceModifier.defaultWeight())
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (live.isTimed) "Start" else "Done set", actionRunCallback<DoneSetAction>(), primary = true, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Pill("End", actionRunCallback<EndWorkoutAction>(), primary = false)
            }
        }

        WorkoutPhase.REST, WorkoutPhase.WORK -> Column(GlanceModifier.fillMaxSize()) {
            if (s.phase == WorkoutPhase.WORK) Header(live) else Row(GlanceModifier.fillMaxWidth()) {
                Text("Rest", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                live.upNext?.let { Caption("Next: $it") }
            }
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                s.phaseEndsAt?.let { Countdown(it) }
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (s.phase == WorkoutPhase.REST) {
                    Pill("+30s", actionRunCallback<AddRestAction>(), primary = false, modifier = GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    Pill("Skip rest", actionRunCallback<SkipAction>(), primary = true, modifier = GlanceModifier.defaultWeight())
                } else {
                    Pill("Done early", actionRunCallback<SkipAction>(), primary = true, modifier = GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.width(8.dp))
                Pill("End", actionRunCallback<EndWorkoutAction>(), primary = false)
            }
        }
    }
}

@Composable
private fun Header(live: LiveWorkout) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            live.exercise?.name ?: live.routine.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        Caption(WorkoutFormat.setProgress(live))
    }
}

/** Live countdown: the system Chronometer ticks every second at no widget-update cost (F5.6). */
@Composable
private fun Countdown(endsAt: Long) {
    val context = LocalContext.current
    val remaining = endsAt - System.currentTimeMillis()
    val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
        setChronometer(R.id.chronometer, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(R.id.chronometer, true)
        setTextColor(R.id.chronometer, GlanceTheme.colors.onSurface.getColor(context).toArgb())
    }
    AndroidRemoteViews(views)
}

/** [−] value [+] stepper; [action] receives delta ±1 (weight steps are 2.5 kg). */
@Composable
private fun Stepper(value: String, unit: String, action: Class<out ActionCallback>, modifier: GlanceModifier) {
    Row(
        modifier.cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", actionRunCallback(action, actionParametersOf(DeltaParam to -1)))
        Text(
            if (unit.isEmpty()) value else "$value $unit",
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            modifier = GlanceModifier.defaultWeight(),
        )
        StepButton("+", actionRunCallback(action, actionParametersOf(DeltaParam to 1)))
    }
}

@Composable
private fun StepButton(label: String, action: Action) {
    Box(
        GlanceModifier.width(40.dp).height(36.dp).cornerRadius(14.dp).background(GlanceTheme.colors.surface).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
internal fun Pill(text: String, action: Action, primary: Boolean, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier.height(36.dp).cornerRadius(18.dp)
            .background(if (primary) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .padding(horizontal = 14.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            maxLines = 1,
            style = TextStyle(
                color = if (primary) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
}

@Composable
private fun Centered(text: String) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) { Caption(text) }
}
