package dev.yxuan.commit.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.yxuan.commit.AppContainer
import dev.yxuan.commit.CommitApp
import dev.yxuan.commit.ui.commitments.CommitmentsViewModel
import dev.yxuan.commit.ui.history.HistoryViewModel
import dev.yxuan.commit.ui.history.SessionDetailViewModel
import dev.yxuan.commit.ui.recap.RecapViewModel
import dev.yxuan.commit.ui.settings.SettingsViewModel
import dev.yxuan.commit.ui.settings.ThemeViewModel
import dev.yxuan.commit.ui.todos.CommitmentDetailViewModel
import dev.yxuan.commit.ui.todos.TodoEditorViewModel
import dev.yxuan.commit.ui.workout.LiveWorkoutViewModel
import dev.yxuan.commit.ui.workout.RoutineEditorViewModel
import dev.yxuan.commit.ui.workout.RoutinesViewModel

private val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as CommitApp).container

/** Builds every screen ViewModel from the [AppContainer]. */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { CommitmentsViewModel(container.commitmentRepository) }
    initializer {
        CommitmentDetailViewModel(createSavedStateHandle(), container.commitmentRepository, container.todoRepository)
    }
    initializer {
        TodoEditorViewModel(createSavedStateHandle(), container.commitmentRepository, container.todoRepository, container.clock)
    }
    initializer { RoutinesViewModel(createSavedStateHandle(), container.routineRepository, container.workoutRepository) }
    initializer { RoutineEditorViewModel(createSavedStateHandle(), container.routineRepository) }
    initializer { LiveWorkoutViewModel(container.workoutRepository) }
    initializer { SettingsViewModel(container.settingsRepository, container.themeRepository) }
    initializer { ThemeViewModel(container.themeRepository) }
    initializer { HistoryViewModel(container.historyRepository, container.clock) }
    initializer { SessionDetailViewModel(createSavedStateHandle(), container.historyRepository) }
    initializer { RecapViewModel(createSavedStateHandle(), container.recapRepository) }
}
