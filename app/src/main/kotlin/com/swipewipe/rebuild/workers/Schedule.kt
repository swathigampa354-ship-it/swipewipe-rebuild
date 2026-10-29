package com.swipewipe.rebuild.workers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.swipewipe.rebuild.GraphManager
import com.swipewipe.rebuild.MediaStoreRepo
import com.swipewipe.rebuild.SettingsStore
import com.swipewipe.rebuild.db.AppDatabase
import java.util.concurrent.TimeUnit

class RebuildWorkerFactory(
    private val db: AppDatabase,
    private val media: MediaStoreRepo,
    private val settings: SettingsStore,
    private val graph: GraphManager
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): androidx.work.ListenableWorker? = when (workerClassName.substringAfterLast('.')) {
        "SimilarImagesWorker" -> SimilarImagesWorker(appContext, workerParameters, db, media)
        "CloudSyncWorker" -> CloudSyncWorker(appContext, workerParameters, db, settings, graph)
        "PhotoCompressWorker" -> PhotoCompressWorker(appContext, workerParameters, media, settings)
        "RemindersWorker" -> RemindersWorker(appContext, workerParameters, db)
        else -> null
    }
}

object Schedule {

    fun scheduleRecurring(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "similar_images_reminder_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SimilarImagesWorker>(12, TimeUnit.HOURS).build()
        )
        wm.enqueueUniquePeriodicWork(
            "otd_reminder_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RemindersWorker>(24, TimeUnit.HOURS).build()
        )
        wm.enqueueUniquePeriodicWork(
            "cloud_provider_sync_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CloudSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()
        )
    }

    fun enqueueOneTime(context: Context, req: androidx.work.WorkRequest) {
        WorkManager.getInstance(context).enqueue(req)
    }
}
