package com.swipewipe.rebuild.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swipewipe.rebuild.SettingsStore
import com.swipewipe.rebuild.db.AppDatabase
import com.swipewipe.rebuild.graph.GraphManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class CloudSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val graph: GraphManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val force = inputData.getBoolean("force_full_resync", false)
        val s = settings.flow.first()
        if (s.graphClientId.isEmpty() || !graph.isSignedIn()) {
            return@withContext Result.success()
        }
        try {
            val photos = graph.listOneDrivePhotos(s.graphClientId)
            graph.insertIntoDb(db, photos)
            if (force) {
                graph.aggregateMonths(photos).let {
                    db.appDao().upsertAggregateMonths(it)
                }
            }
            Result.success()
        } catch (t: Throwable) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
