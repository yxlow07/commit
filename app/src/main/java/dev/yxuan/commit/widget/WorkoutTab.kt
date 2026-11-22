package dev.yxuan.commit.widget

import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.lazy.LazyColumn
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.yxuan.commit.MainActivity
import dev.yxuan.commit.R
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.WorkoutPhase
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.domain.Weekdays
import dev.yxuan.commit.ui.format.WorkoutFormat
import java.time.LocalDate

/**
 * Workout layout (Gym tab, spec "Workout widget"): exercise and position, set progress, then
 * steppers and "Done set" while working, or the countdown with "Add 30 s" / "Skip rest" while
 * resting. All state comes from Room; the countdown is a native Chronometer.
 */
@Composable
fun WorkoutPanel(routines: List<RoutineSummary>, live: LiveWorkout?) {
    when {
        live == null || routines.none { it.routine.id == live.routine.id } -> Idle(routines, runningElsewhere = live != null)
        else -> Live(live)
    }
}

/**
 * No active workout: every routine of this commitment, in the app's order, each with its own Start.
 * Today's planned routine gets the dark button; the others a light one. Scrolls when they don't fit.
 */
@Composable
private fun Idle(routines: List<RoutineSummary>, runningElsewhere: Boolean) {
    val c = LocalWidgetColors.current
    if (routines.isEmpty()) {
        Text("Add a routine in the app to start a workout.", style = TextStyle(color = c.textStrong, fontSize = 15.sp), modifier = GlanceModifier.padding(end = 10.dp))
        return
    }
    val today = LocalDate.now()
    Column(GlanceModifier.fillMaxSize().padding(end = 10.dp)) {
        if (runningElsewhere) {
            Text("Starting one ends the workout in progress.", maxLines = 2, style = TextStyle(color = c.muted, fontSize = 13.sp), modifier = GlanceModifier.padding(bottom = 4.dp))
        }
        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(routines, itemId = { it.routine.id.hashCode().toLong() }) { r -> RoutineRow(r, Weekdays.isPlanned(r.routine.plannedDays, today)) }
        }
    }
}

/** One routine: name, "Planned today, 3 exercises", and Start (needs at least one exercise). */
@Composable
private fun RoutineRow(r: RoutineSummary, planned: Boolean) {
    val c = LocalWidgetColors.current
    Row(GlanceModifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(r.routine.name, maxLines = 1, style = TextStyle(color = c.ink, fontSize = 16.sp, fontWeight = if (planned) FontWeight.Bold else FontWeight.Normal))
            val detail = listOfNotNull(
                if (planned) "Planned today" else null,
                when (r.exerciseCount) { 0 -> "No exercises"; 1 -> "1 exercise"; else -> "${r.exerciseCount} exercises" },
            ).joinToString(", ")
            Text(detail, maxLines = 1, style = TextStyle(color = c.muted, fontSize = 13.sp))
        }
        if (r.exerciseCount > 0) {
            WidgetButton(
                "Start",
                actionRunCallback<StartWorkoutAction>(actionParametersOf(RoutineIdParam to r.routine.id)),
                primary = planned,
                height = 36.dp,
                modifier = GlanceModifier.semantics { contentDescription = "Start ${r.routine.name}" },
            )
        }
    }
}

/** Corner radius of every widget button (matches the app's button token). */
internal val WIDGET_BUTTON_RADIUS = 12.dp

/** From this height the full panel has room for the workout time as a centred headline between sets. */
internal val HERO_TIMER_HEIGHT = 340.dp

/** Below this height (3x2 / 4x2) the workout panel uses its compact form. */
internal val COMPACT_WORKOUT_HEIGHT = 270.dp

@Composable
private fun Live(live: LiveWorkout) {
    val c = LocalWidgetColors.current
    val s = live.state
    val compact = LocalSize.current.height < COMPACT_WORKOUT_HEIGHT
    if (compact && s.phase != WorkoutPhase.COMPLETE) {
        CompactLive(live)
        return
    }
    Column(GlanceModifier.fillMaxSize().padding(end = 10.dp)) {
        if (s.phase == WorkoutPhase.COMPLETE) {
            Text("Workout done", style = TextStyle(color = c.ink, fontSize = 30.sp, fontFamily = WidgetLight))
            Text(WorkoutFormat.summary(live), style = TextStyle(color = c.muted, fontSize = 14.sp))
            Spacer(GlanceModifier.height(16.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton("Open summary", actionStartActivity<MainActivity>(actionParametersOf(OpenWorkoutParam to true)))
                Spacer(GlanceModifier.height(16.dp))
                WidgetButton("Done", actionRunCallback<EndWorkoutAction>(), primary = false)
            }
            return@Column
        }
        val ex = live.exercise ?: return@Column
        // On a tall widget, between sets, the workout time fills the free middle as the headline.
        // During a rest or a timed hold the countdown is the headline, so the time is a label.
        val heroTimer = LocalSize.current.height >= HERO_TIMER_HEIGHT && s.phase == WorkoutPhase.READY && !live.isTimed
        Column(GlanceModifier.fillMaxWidth()) {
            Text(ex.name, maxLines = 1, style = TextStyle(color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WorkoutFormat.position(live) + if (heroTimer) "" else ", ", maxLines = 1, style = TextStyle(color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                if (!heroTimer) Elapsed(live.startedAt)
            }
        }
        Spacer(GlanceModifier.height(12.dp))
        SetProgress(s.setNo, ex.sets)
        if (heroTimer) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Elapsed(live.startedAt, big = true)
            }
        } else {
            Spacer(GlanceModifier.height(14.dp))
        }
        when (s.phase) {
            WorkoutPhase.READY -> if (live.isTimed) {
                Text("${ex.durationSeconds} s", style = TextStyle(color = c.ink, fontSize = 48.sp, fontFamily = WidgetLight, textAlign = TextAlign.Center), modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(16.dp))
                WidgetButton("Start set", actionRunCallback<DoneSetAction>(), primary = true, modifier = GlanceModifier.fillMaxWidth())
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Stepper("${s.reps}", "reps", StepRepsAction::class.java, "Fewer reps", "More reps", GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(12.dp))
                    Stepper(WorkoutFormat.weightValue(s.weight), "kg", StepWeightAction::class.java, "Less weight", "More weight", GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(16.dp))
                WidgetButton("Done set", actionRunCallback<DoneSetAction>(), primary = true, modifier = GlanceModifier.fillMaxWidth())
            }
            WorkoutPhase.REST, WorkoutPhase.WORK -> {
                s.phaseEndsAt?.let { Countdown(it) }
                val under = if (s.phase == WorkoutPhase.REST) WorkoutFormat.restThen(live)?.let { "Rest, then $it" } ?: "Rest" else "Hold, then rest"
                Text(under, maxLines = 1, style = TextStyle(color = c.muted, fontSize = 14.sp, textAlign = TextAlign.Center), modifier = GlanceModifier.fillMaxWidth())
                Spacer(GlanceModifier.height(16.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    if (s.phase == WorkoutPhase.REST) {
                        WidgetButton("Add 30 s", actionRunCallback<AddRestAction>(), primary = false, modifier = GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetButton("Skip rest", actionRunCallback<SkipAction>(), primary = true, modifier = GlanceModifier.defaultWeight())
                    } else {
                        WidgetButton("Done early", actionRunCallback<SkipAction>(), primary = true, modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
            WorkoutPhase.COMPLETE -> Unit
        }
    }
}

/**
 * Short widget: one header line (exercise or "Rest, then set 2", with "Set 1 of 2"), then the
 * steppers or a smaller countdown, then a 44 dp button row. Same actions as the full panel.
 */
@Composable
private fun CompactLive(live: LiveWorkout) {
    val c = LocalWidgetColors.current
    val s = live.state
    val ex = live.exercise ?: return
    val resting = s.phase == WorkoutPhase.REST
    val title = when {
        resting -> WorkoutFormat.restThen(live)?.let { "Rest, then $it" } ?: "Rest"
        s.phase == WorkoutPhase.WORK -> "${ex.name}, hold"
        else -> ex.name
    }
    Column(GlanceModifier.fillMaxSize().padding(end = 10.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, maxLines = 1, style = TextStyle(color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
            Text("Set ${s.setNo} of ${ex.sets}", maxLines = 1, style = TextStyle(color = c.muted, fontSize = 13.sp))
            Text(", ", style = TextStyle(color = c.muted, fontSize = 13.sp))
            Elapsed(live.startedAt)
        }
        Spacer(GlanceModifier.height(6.dp))
        when (s.phase) {
            WorkoutPhase.READY -> {
                if (live.isTimed) {
                    Text("${ex.durationSeconds} s", style = TextStyle(color = c.ink, fontSize = 28.sp, fontFamily = WidgetLight))
                } else {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Stepper("${s.reps}", "", StepRepsAction::class.java, "Fewer reps", "More reps", GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(8.dp))
                        Stepper(WorkoutFormat.weightValue(s.weight), "", StepWeightAction::class.java, "Less weight", "More weight", GlanceModifier.defaultWeight())
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                WidgetButton(if (live.isTimed) "Start set" else "Done set", actionRunCallback<DoneSetAction>(), primary = true, modifier = GlanceModifier.fillMaxWidth(), height = 44.dp)
            }
            WorkoutPhase.REST, WorkoutPhase.WORK -> {
                s.phaseEndsAt?.let { Countdown(it, small = true) }
                Spacer(GlanceModifier.defaultWeight())
                Row(GlanceModifier.fillMaxWidth()) {
                    if (resting) {
                        WidgetButton("Add 30 s", actionRunCallback<AddRestAction>(), primary = false, modifier = GlanceModifier.defaultWeight(), height = 44.dp)
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetButton("Skip rest", actionRunCallback<SkipAction>(), primary = true, modifier = GlanceModifier.defaultWeight(), height = 44.dp)
                    } else {
                        WidgetButton("Done early", actionRunCallback<SkipAction>(), primary = true, modifier = GlanceModifier.defaultWeight(), height = 44.dp)
                    }
                }
            }
            WorkoutPhase.COMPLETE -> Unit
        }
    }
}

/** One 4 dp segment per set (done `ink`, current `faint`, future `track`) and "Set 3 of 4". */
@Composable
private fun SetProgress(setNo: Int, total: Int) {
    val c = LocalWidgetColors.current
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.defaultWeight()) {
            for (n in 1..total.coerceAtLeast(1)) {
                if (n > 1) Spacer(GlanceModifier.width(4.dp))
                val color = when {
                    n < setNo -> c.ink
                    n == setNo -> c.faint
                    else -> c.track
                }
                Box(GlanceModifier.defaultWeight().height(4.dp).cornerRadius(2.dp).background(color)) {}
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Text("Set $setNo of $total", style = TextStyle(color = c.muted, fontSize = 13.sp))
    }
}

/**
 * Time since the workout started, counting up on its own (a system Chronometer, no widget updates):
 * [big] is the tall panel's 56 sp centred headline, otherwise a 13 sp label.
 */
@Composable
private fun Elapsed(startedAt: Long, big: Boolean = false) {
    val context = LocalContext.current
    val c = LocalWidgetColors.current
    val views = RemoteViews(context.packageName, if (big) R.layout.widget_elapsed_big else R.layout.widget_elapsed).apply {
        setChronometer(R.id.chronometer, SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startedAt), null, true)
        setTextColor(R.id.chronometer, (if (big) c.ink else c.muted).getColor(context).toArgb())
    }
    // Fixed width: a RemoteViews child otherwise stretches and pushes the exercise name out.
    val size = if (big) GlanceModifier.fillMaxWidth().height(64.dp) else GlanceModifier.width(56.dp).height(16.dp)
    AndroidRemoteViews(views, modifier = size.semantics { contentDescription = "Workout time" })
}

/** Live countdown: the system Chronometer ticks every second at no widget-update cost (F5.6). */
@Composable
private fun Countdown(endsAt: Long, small: Boolean = false) {
    val context = LocalContext.current
    val c = LocalWidgetColors.current
    val remaining = endsAt - System.currentTimeMillis()
    val layout = if (small) R.layout.widget_chronometer_small else R.layout.widget_chronometer
    val views = RemoteViews(context.packageName, layout).apply {
        setChronometer(R.id.chronometer, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(R.id.chronometer, true)
        setTextColor(R.id.chronometer, c.ink.getColor(context).toArgb())
    }
    // Fixed height: a RemoteViews child otherwise takes all the space left and pushes the buttons out.
    AndroidRemoteViews(views, modifier = GlanceModifier.fillMaxWidth().height(if (small) 46.dp else 86.dp))
}

/** [−] value/unit [+]: 44 dp `fill` circles, value 28 sp light, unit 12 sp `muted` under it. */
@Composable
private fun Stepper(value: String, unit: String, action: Class<out ActionCallback>, minusLabel: String, plusLabel: String, modifier: GlanceModifier) {
    val c = LocalWidgetColors.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(R.drawable.widget_minus, minusLabel, actionRunCallback(action, actionParametersOf(DeltaParam to -1)))
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, maxLines = 1, style = TextStyle(color = c.ink, fontSize = 28.sp, fontFamily = WidgetLight, textAlign = TextAlign.Center))
            if (unit.isNotEmpty()) Text(unit, style = TextStyle(color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center))
        }
        StepButton(R.drawable.widget_plus, plusLabel, actionRunCallback(action, actionParametersOf(DeltaParam to 1)))
    }
}

@Composable
private fun StepButton(icon: Int, label: String, action: Action) {
    val c = LocalWidgetColors.current
    Box(
        GlanceModifier.size(44.dp).cornerRadius(WIDGET_BUTTON_RADIUS).background(c.fill).clickable(action).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(c.ink), modifier = GlanceModifier.size(20.dp))
    }
}

/** Primary (`ink`) or secondary (`fill`) button, 48 dp tall, 12 dp corners. */
@Composable
internal fun WidgetButton(text: String, action: Action, primary: Boolean, modifier: GlanceModifier = GlanceModifier, height: androidx.compose.ui.unit.Dp = 48.dp) {
    val c = LocalWidgetColors.current
    Box(
        modifier.height(height).cornerRadius(WIDGET_BUTTON_RADIUS).background(if (primary) c.ink else c.fill).padding(horizontal = 18.dp).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, maxLines = 1, style = TextStyle(color = if (primary) c.onInk else c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    }
}

/** Text button: no background, `ink` Bold. */
@Composable
private fun TextButton(text: String, action: Action) {
    val c = LocalWidgetColors.current
    Text(text, style = TextStyle(color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.padding(vertical = 12.dp).clickable(action))
}
