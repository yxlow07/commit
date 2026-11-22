package dev.yxuan.commit.ui

import dev.yxuan.commit.ui.settings.ArchivedViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.yxuan.commit.AppContainer
import dev.yxuan.commit.CommitApp
import dev.yxuan.commit.ui.history.HistoryViewModel
import dev.yxuan.commit.ui.history.SessionDetailViewModel
import dev.yxuan.commit.ui.recap.RecapViewModel
import dev.yxuan.commit.ui.settings.SettingsViewModel
import dev.yxuan.commit.ui.settings.ThemeViewModel
import dev.yxuan.commit.ui.todos.CommitmentDetailViewModel
import dev.yxuan.commit.ui.todos.TodoEditorViewModel
import dev.yxuan.commit.ui.today.TodayViewModel
import dev.yxuan.commit.ui.workout.LiveWorkoutViewModel
import dev.yxuan.commit.ui.workout.RoutineEditorViewModel
import dev.yxuan.commit.ui.workout.WorkoutsViewModel

private val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as CommitApp).container

/** Builds every screen ViewModel from the [AppContainer]. */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { TodayViewModel(container.commitmentRepository, container.todoRepository, container.routineRepository, container.clock) }
    initializer {
        CommitmentDetailViewModel(createSavedStateHandle(), container.commitmentRepository, container.todoRepository)
    }
    initializer {
        TodoEditorViewModel(createSavedStateHandle(), container.commitmentRepository, container.todoRepository, container.clock)
    }
    initializer { WorkoutsViewModel(container.commitmentRepository, container.routineRepository, container.workoutRepository) }
    initializer { RoutineEditorViewModel(createSavedStateHandle(), container.routineRepository) }
    initializer { LiveWorkoutViewModel(container.workoutRepository) }
    initializer { SettingsViewModel(container.settingsRepository, container.themeRepository, container.commitmentRepository) }
    initializer { ThemeViewModel(container.themeRepository) }
    initializer { ArchivedViewModel(container.commitmentRepository) }
    initializer { HistoryViewModel(container.historyRepository, container.clock) }
    initializer { SessionDetailViewModel(createSavedStateHandle(), container.historyRepository) }
    initializer { RecapViewModel(createSavedStateHandle(), container.recapRepository) }
}
