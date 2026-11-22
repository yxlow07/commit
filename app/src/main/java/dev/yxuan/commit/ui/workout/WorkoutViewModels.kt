package dev.yxuan.commit.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.yxuan.commit.data.db.dao.RoutineSummary
import dev.yxuan.commit.data.db.entity.CommitmentEntity
import dev.yxuan.commit.data.db.entity.CommitmentType
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.db.entity.ExerciseEntity
import dev.yxuan.commit.data.db.entity.RoutineEntity
import dev.yxuan.commit.data.repo.ExerciseInput
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.data.repo.RoutineRepository
import dev.yxuan.commit.data.repo.WorkoutRepository
import dev.yxuan.commit.ui.RoutineEditorRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun <T> ViewModel.state(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
    flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** The Workouts tab: every routine of the active workout commitments (F5.1), the live workout, Start. */
class WorkoutsViewModel(
    commitments: CommitmentRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {
    /** Active workout commitments; null until loaded. */
    val workoutCommitments: StateFlow<List<CommitmentEntity>?> =
        state(commitments.observeActive().map { list -> list.filter { it.type == CommitmentType.WORKOUT } }, null)

    val list: StateFlow<List<RoutineSummary>> = state(
        combine(routines.observeSummaries(), workoutCommitments) { all, owners ->
            val ids = owners.orEmpty().map { it.id }.toSet()
            all.filter { it.routine.commitmentId in ids }
        },
        emptyList(),
    )
    val live: StateFlow<LiveWorkout?> = state(workouts.observeLive(), null)

    private val commitmentRepo = commitments

    fun create(commitmentId: String, name: String, then: (String) -> Unit) {
        viewModelScope.launch { then(routines.createRoutine(commitmentId, name)) }
    }

    fun createCommitment(name: String, color: Int) {
        viewModelScope.launch { commitmentRepo.create(name, CommitmentType.WORKOUT, color) }
    }

    /** Workout commitments are edited here: Today and the detail screen send them to Workouts. */
    fun updateCommitment(id: String, name: String, color: Int, showInWidget: Boolean) {
        viewModelScope.launch { commitmentRepo.update(id, name, color, showInWidget) }
    }

    fun archiveCommitment(id: String) {
        viewModelScope.launch { commitmentRepo.archive(id) }
    }

    fun start(id: String, then: () -> Unit) {
        viewModelScope.launch {
            workouts.start(id)
            then()
        }
    }
}

/** Edit a routine and its exercises (F5.1). */
class RoutineEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val routines: RoutineRepository,
) : ViewModel() {
    val routineId: String = savedStateHandle.toRoute<RoutineEditorRoute>().id

    val routine: StateFlow<RoutineEntity?> = state(routines.observe(routineId), null)
    val exercises: StateFlow<List<ExerciseEntity>> = state(routines.observeExercises(routineId), emptyList())

    fun saveRoutine(name: String, restBetweenExercisesS: Int, plannedDays: Int) =
        launch { routines.updateRoutine(routineId, name, restBetweenExercisesS, plannedDays) }

    fun saveExercise(id: String?, input: ExerciseInput) = launch { routines.saveExercise(routineId, id, input) }
    fun deleteExercise(id: String) = launch { routines.deleteExercise(id) }
    fun moveExercise(id: String, delta: Int) = launch { routines.moveExercise(id, delta) }
    fun deleteRoutine() = launch { routines.deleteRoutine(routineId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** The running workout in the app; same actions as the widget. */
class LiveWorkoutViewModel(private val workouts: WorkoutRepository) : ViewModel() {
    val live: StateFlow<LiveWorkout?> = state(workouts.observeLive(), null)

    fun primary() = launch { workouts.primary() }
    fun skip() = launch { workouts.skip() }
    fun addRest() = launch { workouts.addRest() }
    fun stepReps(delta: Int) = launch { workouts.stepReps(delta) }
    fun stepWeight(delta: Int) = launch { workouts.stepWeight(delta) }
    fun end() = launch { workouts.end() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
