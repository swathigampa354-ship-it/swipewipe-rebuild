package com.swipewipe.rebuild.ui

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.swipewipe.rebuild.App
import com.swipewipe.rebuild.GalleryItem
import com.swipewipe.rebuild.db.BookmarkEntity
import com.swipewipe.rebuild.db.CompletedPeriodEntity
import com.swipewipe.rebuild.db.LocalDecisionReconstructionEntity
import com.swipewipe.rebuild.db.MediaDecisionEntity
import com.swipewipe.rebuild.db.StatsEntity
import com.swipewipe.rebuild.workers.CloudSyncWorker
import com.swipewipe.rebuild.workers.PhotoCompressWorker
import com.swipewipe.rebuild.workers.SimilarImagesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppVm(app: Application) : AndroidViewModel(app) {

    internal val a get() = getApplication<App>()

    val gallery = MutableStateFlow<List<GalleryItem>>(emptyList())
    val loading = MutableStateFlow(false)
    val similarReloadTick = MutableStateFlow(0)
    val toast = MutableStateFlow<String?>(null)

    val decided: SnapshotStateMap<String, String> = mutableStateMapOf()

    var lastCardIndex = 0

    private val decisionStack = ArrayDeque<GalleryItem>()

    fun deckKind() = "local"

    fun loadGallery() {
        viewModelScope.launch {
            loading.value = true
            val items = withContext(Dispatchers.IO) {
                a.media.loadGallery(prefs().sortOrder)
            }
            gallery.value = items
            val decisions = withContext(Dispatchers.IO) {
                a.db.appDao().forSource("image") + a.db.appDao().forSource("video")
            }
            decided.clear()
            decisions.forEach { decided[key(it.mediaId, it.mediaSource)] = it.decision }
            loading.value = false
        }
    }

    private fun key(id: String, source: String) = "$source|$id"

    fun deckOf(sourceFilter: String?): List<GalleryItem> {
        val seen = decided
        return gallery.value.filter { item ->
            (sourceFilter == null || item.mediaSource == sourceFilter) &&
                seen[key(item.id, item.mediaSource)] == null
        }
    }

    fun decideKeep(item: GalleryItem) {
        decisionStack.addLast(item)
        decided[key(item.id, item.mediaSource)] = "keep"
        record(true, item, "keep", 0)
    }

    fun decideDelete(item: GalleryItem, onDeleted: (Int) -> Unit) {
        decisionStack.addLast(item)
        decided[key(item.id, item.mediaSource)] = "delete"
        viewModelScope.launch(Dispatchers.IO) {
            val before = a.db.appDao().observeAllStats().sumOf { it.totalSize }
            val outcome = a.media.deleteMedia(item.mediaSource, listOf(item.id))
            val freed = if (item.mediaSource == "image" && item.sizeBytes > 0) item.sizeBytes else 0
            record(true, item, "delete", freed)
            b().recordDelete(1)
            withContext(Dispatchers.Main) {
                onDeleted(item.sizeBytes)
            }
        }
    }

    fun decideBookmark(item: GalleryItem) {
        decided[key(item.id, item.mediaSource)] = "bookmark"
        viewModelScope.launch(Dispatchers.IO) {
            a.db.appDao().add(BookmarkEntity(item.id, item.mediaSource))
            record(true, item, "bookmark", 0)
        }
    }

    private suspend fun record(seen: Boolean, item: GalleryItem, decision: String, freed: Long) {
        val dao = a.db.appDao()
        dao.upsert(MediaDecisionEntity(item.mediaSource, item.id, decision))
        dao.add(
            LocalDecisionReconstructionEntity(
                mediaSource = item.mediaSource,
                mediaId = item.id,
                decision = decision
            )
        )
        val current = dao.observeAllStats().firstOrNull { it.mediaSource == item.mediaSource }
        dao.upsert(
            StatsEntity(
                mediaSource = item.mediaSource,
                swipedCount = (current?.swipedCount ?: 0) + 1,
                deletedCount = (current?.deletedCount ?: 0) + if (decision == "delete") 1 else 0,
                totalSize = (current?.totalSize ?: 0) + freed,
                timesSeenCount = (current?.timesSeenCount ?: 0) + 1
            )
        )
        maybeCompleteMonth(item, decision)
    }

    private suspend fun maybeCompleteMonth(item: GalleryItem, decision: String) {
        if (decision == "delete") {
            val d = java.util.Calendar.getInstance()
            val dao = a.db.appDao()
            dao.upsertAll(
                listOf(
                    CompletedPeriodEntity(
                        mediaSource = item.mediaSource,
                        year = d.get(java.util.Calendar.YEAR),
                        month = d.get(java.util.Calendar.MONTH) + 1
                    )
                )
            )
        }
    }

    fun undoLast(): Boolean {
        val item = decisionStack.removeLastOrNull() ?: return false
        decided.remove(key(item.id, item.mediaSource))
        viewModelScope.launch(Dispatchers.IO) {
            val dao = a.db.appDao()
            dao.removeDecision(item.mediaSource, item.id)
        }
        return true
    }

    fun removeBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            a.db.appDao().removeBookmark(bookmark.mediaId, bookmark.mediaSource)
            decided.remove(key(bookmark.mediaId, bookmark.mediaSource))
            similarReloadTick.value++
        }
    }

    fun bulkDelete(ids: List<String>, source: String, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = a.media.deleteMedia(source, ids)
            val freed = ids.size
            outcome.deletedIds.forEach { id ->
                decided[key(id, source)] = "delete"
                a.db.appDao().upsert(MediaDecisionEntity(source, id, "delete"))
                a.db.appDao().add(
                    LocalDecisionReconstructionEntity(
                        mediaSource = source, mediaId = id, decision = "delete"
                    )
                )
            }
            withContext(Dispatchers.Main) { onDone(outcome.deletedIds.size) }
        }
    }

    fun loadGroups(onReady: (List<List<GalleryItem>>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = a.db.appDao()
            val groupIds = dao.duplicateGroupIds()
            val galleryById = gallery.value.associateBy { it.id }
            val itemsInGroups =
                if (groupIds.isEmpty()) emptyList()
                else dao.byGroups(groupIds.take(200))
            val groupLists = groupIds.take(50).map { gid ->
                itemsInGroups.filter { (it.groupId ?: it.id) == gid }.mapNotNull { sig ->
                    galleryById[sig.mediaId]
                }
            }.filter { it.size > 1 }
            withContext(Dispatchers.Main) { onReady(groupLists.filter { it.isNotEmpty() }) }
        }
    }

    fun runSimilarScan() {
        val req = OneTimeWorkRequestBuilder<SimilarImagesWorker>().build()
        WorkManager.getInstance(a).enqueue(req)
        viewModelScope.launch {
            similarReloadTick.value++
        }
    }

    fun compressNow(ids: List<GalleryItem>) {
        val req = OneTimeWorkRequestBuilder<PhotoCompressWorker>()
            .setInputData(
                workDataOf(PhotoCompressWorker.KEY_IDS to ids.map { it.id }.toTypedArray())
            )
            .build()
        WorkManager.getInstance(a).enqueue(req)
        toast.value = "Compression batch queued"
    }

    fun runCloudSync(force: Boolean) {
        val req = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setInputData(workDataOf("force_full_resync" to force))
            .build()
        WorkManager.getInstance(a).enqueue(req)
        toast.value = "Cloud sync queued${if (force) " (full resync)" else ""}"
    }

    private fun prefs() = a.settings

    private fun b() = a.settings
}
