package dev.yxuan.commit.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.yxuan.commit.appContainer

val RoutineIdParam = ActionParameters.Key<String>("routine_id")
val DeltaParam = ActionParameters.Key<Int>("delta")
val OpenWorkoutParam = ActionParameters.Key<Boolean>(dev.yxuan.commit.MainActivity.EXTRA_OPEN_WORKOUT)
val OpenSessionParam = ActionParameters.Key<String>(dev.yxuan.commit.MainActivity.EXTRA_OPEN_SESSION)

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

/** "Skip set": pass over the waiting set without logging it. */
class SkipSetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.skipSet()
    }
}

/** "End exercise": skip this exercise's remaining sets and go to the next exercise. */
class EndExerciseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.endExercise()
    }
}

/** "One more set": another set of the exercise just finished. */
class ExtraSetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.workoutRepository.extraSet()
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
