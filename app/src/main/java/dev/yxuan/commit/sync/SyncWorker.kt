package dev.yxuan.commit.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.yxuan.commit.appContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * One sync round in the background (PRD sync rules 1, 6, 7): after local writes, every 15 minutes,
 * and when an FCM ping says the web app changed something.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        return runCatching { container.syncManager.syncOnce() }
            .onFailure { Log.w(TAG, "sync failed", it) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val NOW = "sync-now"
        private const val PERIODIC = "sync-periodic"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Runs as soon as there's network; repeated calls while one is pending are merged. */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(network)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }

        /** Safety net every 15 minutes (PRD rule 6). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** FCM data messages from the `notify-sync` Edge Function wake the phone to sync (PRD rule 7). */
class CommitMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(TAG, "push received: ${message.data}")
        if (message.data["type"] == "sync") SyncWorker.enqueueNow(applicationContext)
    }

    override fun onNewToken(token: String) {
        val container = applicationContext.appContainer
        container.appScope.launch { container.syncManager.registerPushToken(token) }
    }
}

/** FCM is optional: only used when the app was built with a google-services.json. */
object PushTokens {
    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun refresh(context: Context, sync: SyncManager) {
        if (!isAvailable(context)) return
        runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()?.let { sync.registerPushToken(it) }
    }
}

private const val TAG = "CommitSync"
