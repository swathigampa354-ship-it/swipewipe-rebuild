package com.swipewipe.rebuild.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swipewipe.rebuild.MediaStoreRepo
import com.swipewipe.rebuild.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CompressState {
    val cancelled = AtomicBoolean(false)
    @Volatile var lastProgressDone: Int = 0
    @Volatile var lastProgressTotal: Int = 0
}

class PhotoCompressWorker(
    context: Context,
    params: WorkerParameters,
    private val media: MediaStoreRepo,
    private val settings: SettingsStore
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_IDS = "photo_compress_batch_ids"
        const val CHANNEL = "media_tools_progress"
        const val NOTIF_ID = 4711
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        CompressState.cancelled.set(false)
        val ids = inputData.getStringArray(KEY_IDS)?.toList().orEmpty()
        if (ids.isEmpty()) {
            CompressState.lastProgressDone = 0
            CompressState.lastProgressTotal = 0
            return@withContext Result.success()
        }
        val quality = settings.flow.first().compressQuality
        val outDir = media.createOutputCollection()
        var done = 0
        var bytes = 0L
        notify(if (outDir.isDirectory) outDir.path else "", done, ids.size, bytes, true)
        for (id in ids) {
            if (CompressState.cancelled.get() || isStopped) break
            val nf = runCatching {
                media.compressInto(
                    android.net.Uri.withAppendedPath(MediaStoreRepo.collectionUri(false), id),
                    outDir,
                    "img_$id.jpg",
                    quality
                )
            }.getOrDefault(-1L)
            if (nf > 0) bytes += nf
            done++
            CompressState.lastProgressDone = done
            CompressState.lastProgressTotal = ids.size
            notify(outDir.path, done, ids.size, bytes, done < ids.size)
        }
        notify(outDir.path, done, ids.size, bytes, false)
        CompressState.lastProgressDone = 0
        CompressState.lastProgressTotal = 0
        if (CompressState.cancelled.get()) Result.failure() else Result.success()
    }

    private fun notify(outDirPath: String, done: Int, total: Int, bytes: Long, ongoing: Boolean) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val cancel = PendingIntent.getBroadcast(
            applicationContext, 0,
            android.content.Intent(applicationContext, PhotoCompressCancelReceiver::class.java)
                .setAction(PhotoCompressCancelReceiver.ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (total > 0) {
            "$done / $total • ${bytes / 1024 / 1024} MB written to $outDirPath"
        } else {
            "Finished"
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(if (CompressState.cancelled.get()) "Compression cancelled" else "Compressing photos")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(if (total > 0) total else 1, done, total == 0)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
        if (!ongoing) {
            runCatching {
                val b = NotificationCompat.Builder(applicationContext, CHANNEL)
                    .setContentTitle("Photo compression finished")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .build()
                nm.notify(NOTIF_ID + 1, b)
            }
        }
    }
}
