package com.swipewipe.rebuild.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swipewipe.rebuild.MediaStoreRepo
import com.swipewipe.rebuild.db.AppDatabase
import com.swipewipe.rebuild.db.SimilarImageEntity
import com.swipewipe.rebuild.util.PhotoIntelligence
import kotlinx.coroutines.withContext

class SimilarImagesWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
    private val media: MediaStoreRepo
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!media.hasMediaPermission()) {
            return@withContext Result.success()
        }
        val items = media.loadGallery("date_added DESC").filter { !it.isVideo }.take(1200)
        val dao = db.appDao()
        dao.clear()

        val rows = ArrayList<SimilarImageEntity>(items.size)
        for (item in items) {
            val sig = runCatching {
                media.openInput(android.net.Uri.parse(item.uri))?.use { PhotoIntelligence.averageHash(it) }
            }.getOrNull() ?: continue
            if (sig.isEmpty()) continue
            rows.add(
                SimilarImageEntity(
                    id = item.id,
                    mediaSource = item.mediaSource,
                    mediaId = item.id,
                    signatureBytes = sig,
                    groupId = null,
                    modifiedMs = item.dateTakenMs
                )
            )
        }
        dao.upsertSimilar(rows)

        val threshold = PhotoIntelligence.similarDistanceThreshold()
        val byGroup = HashMap<String, MutableList<SimilarImageEntity>>()
        outer@ for (row in rows) {
            for ((gid, members) in byGroup) {
                for (member in members) {
                    val d = PhotoIntelligence.hammingDistance(
                        row.signatureBytes, member.signatureBytes
                    )
                    if (d <= threshold) {
                        byGroup[gid] = members + row
                        continue@outer
                    }
                }
            }
            val gid = PhotoIntelligence.sha256Prefix(row.signatureBytes)
            byGroup[gid] = mutableListOf(row)
        }
        val assignments = HashMap<String, String>(rows.size)
        byGroup.forEach { (gid, members) ->
            if (members.size > 1) {
                members.forEach { assignments[it.id] = gid }
            }
        }
        dao.assignGroups(assignments)
        Result.success()
    }
}
