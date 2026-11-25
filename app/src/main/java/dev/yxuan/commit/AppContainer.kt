package dev.yxuan.commit

import android.content.Context
import dev.yxuan.commit.alarm.AlarmScheduler
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.repo.CommitmentRepository
import dev.yxuan.commit.data.repo.DataChangeListener
import dev.yxuan.commit.data.repo.HistoryRepository
import dev.yxuan.commit.data.repo.LiveWorkout
import dev.yxuan.commit.data.repo.RecapRepository
import dev.yxuan.commit.data.repo.RoutineRepository
import dev.yxuan.commit.data.repo.SettingsRepository
import dev.yxuan.commit.data.repo.TodoRepository
import dev.yxuan.commit.data.repo.WorkoutEffects
import dev.yxuan.commit.data.repo.WorkoutRepository
import dev.yxuan.commit.notify.NotificationActionReceiver
import dev.yxuan.commit.notify.WorkoutNotification
import dev.yxuan.commit.sync.SyncManager
import dev.yxuan.commit.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Manual dependency container. Lives on [CommitApp] so activities, the widget, receivers and
 * workers can all reach the same instances.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = DeviceClock

    /** Process-wide scope for work started by receivers and widget actions. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val widgetUpdater = WidgetUpdater(appContext)

    val alarmScheduler by lazy { AlarmScheduler(appContext, database, clock) }

    /** After every local write: redraw widgets, reschedule alarms, and make sure the write uploads. */
    private val changeListener = DataChangeListener {
        widgetUpdater.refresh()
        alarmScheduler.rescheduleTodos()
        syncManager.requestSync()
    }

    /** Sync (M7). After downloads from the server: redraw and reschedule (PRD sync rule 7). */
    val syncManager by lazy {
        SyncManager(appContext, database, appScope, collectionPhotos) {
            widgetUpdater.refresh()
            alarmScheduler.rescheduleTodos()
            alarmScheduler.scheduleRecaps()
        }
    }

    val commitmentRepository by lazy {
        CommitmentRepository(database.commitmentDao(), clock, changeListener)
    }

    val todoRepository by lazy {
        TodoRepository(database, clock, changeListener)
    }

    val collectionPhotos by lazy { dev.yxuan.commit.data.photo.CollectionPhotos(appContext) }

    /** Collections are standalone: no widget, alarms or recaps, so edits only need to sync. */
    val collectionRepository by lazy {
        dev.yxuan.commit.data.repo.CollectionRepository(database, clock, collectionPhotos) { syncManager.requestSync() }
    }

    val settingsRepository by lazy {
        SettingsRepository(database, clock) {
            // Recap and chore reminder times (and the theme) live in settings; upload right away.
            alarmScheduler.rescheduleTodos()
            alarmScheduler.scheduleRecaps()
            widgetUpdater.refresh()
            syncManager.requestSync()
        }
    }

    val themeRepository by lazy {
        dev.yxuan.commit.data.repo.ThemeRepository(database, clock, settingsRepository, changeListener)
    }

    val recapRepository by lazy { RecapRepository(database, clock) }

    val historyRepository by lazy { HistoryRepository(database, clock, changeListener) }

    val routineRepository by lazy {
        RoutineRepository(database, clock) {
            widgetUpdater.refresh()
            syncManager.requestSync() // routine/exercise edits upload right away too
        }
    }

    private val workoutEffects = object : WorkoutEffects {
        override fun setTimer(at: Long?) {
            if (at != null) alarmScheduler.setWorkoutTimer(at) else alarmScheduler.cancelWorkoutTimer()
        }

        override suspend fun changed(live: LiveWorkout?) {
            WorkoutNotification.update(appContext, live)
            widgetUpdater.refresh()
        }

        override fun alert(live: LiveWorkout) = WorkoutNotification.alert(appContext, live)
    }

    val workoutRepository by lazy { WorkoutRepository(database, clock, workoutEffects) }

    /** Workout timer alarm fired (or alarms are being restored): advance the live workout. */
    suspend fun onWorkoutTimer() = workoutRepository.onTimer()

    /** A workout button on the ongoing notification was tapped (F5.7). */
    suspend fun onWorkoutAction(action: String) {
        when (action) {
            NotificationActionReceiver.ACTION_WORKOUT_DONE_SET -> workoutRepository.primary()
            NotificationActionReceiver.ACTION_WORKOUT_ADD_REST -> workoutRepository.addRest()
            NotificationActionReceiver.ACTION_WORKOUT_SKIP_REST -> workoutRepository.skip()
        }
    }
}

/** System clock that always reports the current device time zone, so zone changes apply immediately. */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as CommitApp).container
