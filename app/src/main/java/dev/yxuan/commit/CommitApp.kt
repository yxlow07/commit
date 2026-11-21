package dev.yxuan.commit

import android.app.Application
import dev.yxuan.commit.notify.Notifications
import kotlinx.coroutines.launch

class CommitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.syncManager.start()
        // Cheap and idempotent: make sure the daily redraw and todo alarms are armed.
        container.appScope.launch {
            container.alarmScheduler.scheduleMidnight()
            container.alarmScheduler.scheduleRecaps()
            container.alarmScheduler.rescheduleTodos()
        }
    }
}
