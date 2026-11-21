package dev.yxuan.commit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.yxuan.commit.appContainer

val RoutineIdParam = ActionParameters.Key<String>("routine_id")
val DeltaParam = ActionParameters.Key<Int>("delta")

// Workout tab buttons (F5.2–F5.4). Each one writes through WorkoutRepository, which redraws the widget.

class StartWorkoutAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val routineId = parameters[RoutineIdParam] ?: return
        context.appContainer.workoutRepository.start(routineId)
    }
}

/** "Done set", or "Start" for a timed exercise. */
class DoneSetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.primary()
    }
}

class StepRepsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.stepReps(parameters[DeltaParam] ?: return)
    }
}

/** Weight stepper: delta is in steps of 2.5 kg. */
class StepWeightAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.stepWeight(parameters[DeltaParam] ?: return)
    }
}

class AddRestAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.addRest()
    }
}

class SkipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.skip()
    }
}

/** End the workout early, or dismiss the "Workout complete" summary (F5.8). */
class EndWorkoutAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.end()
    }
}
