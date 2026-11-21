package dev.yxuan.commit.data.repo

/**
 * Notified after every local write so dependants can react (the widget redraws now;
 * alarm rescheduling and sync enqueueing hook in here in M3 and M7).
 */
fun interface DataChangeListener {
    suspend fun onDataChanged()
}
