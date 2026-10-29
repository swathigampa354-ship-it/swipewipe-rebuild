package com.swipewipe.rebuild

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class GalleryItem(
    val id: String,
    val isVideo: Boolean,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val dateTakenMs: Long,
    val mediaSource: String = "local"
)

class MediaStoreRepo(private val context: Context) {

    companion object {
        fun collectionUri(video: Boolean): Uri =
            if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        fun sourceOf(video: Boolean) = if (video) "video" else "image"
    }

    val contentResolver get() = context.contentResolver

    fun hasMediaPermission(): Boolean {
        val primary = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
        val selected = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
        return primary || selected
    }

    suspend fun loadGallery(sortOrder: String = "date_added DESC"): List<GalleryItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<GalleryItem>(2048)
            if (!hasMediaPermission()) return@withContext out
            for (isVideo in listOf(false, true)) {
                val uri = collectionUri(isVideo)
                val proj = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED
                )
                runCatching {
                    contentResolver.query(uri, proj, null, null, sortOrder)?.use { c ->
                        while (c.moveToNext()) {
                            val id = c.getLong(0)
                            val name = c.getString(1) ?: "media_$id"
                            val size = c.getLong(2)
                            val addedSec = c.getLong(3)
                            val itemUri = Uri.withAppendedPath(uri, id.toString())
                            out.add(
                                GalleryItem(
                                    id = id.toString(),
                                    isVideo = isVideo,
                                    uri = itemUri.toString(),
                                    displayName = name,
                                    sizeBytes = size,
                                    dateTakenMs = addedSec * 1000L,
                                    mediaSource = sourceOf(isVideo)
                                )
                            )
                        }
                    }
                }
            }
            out
        }

    fun contentUri(item: GalleryItem): Uri = Uri.parse(item.uri)

    data class DeleteOutcome(val deletedIds: Set<String>, val needPrompt: List<Pair<String, Uri>>)

    suspend fun deleteMedia(source: String, ids: List<String>): DeleteOutcome =
        withContext(Dispatchers.IO) {
            val base = collectionUri(source == "video")
            val ok = HashSet<String>()
            val need = ArrayList<Pair<String, Uri>>()
            ids.forEach { idRaw ->
                try {
                    val itemUri = Uri.withAppendedPath(base, idRaw)
                    val rows = contentResolver.delete(itemUri, null, null)
                    if (rows > 0) ok.add(idRaw) else need.add(idRaw to itemUri)
                } catch (r: RecoverableSecurityException) {
                    need.add(idRaw to Uri.withAppendedPath(base, idRaw))
                } catch (t: Throwable) {
                    need.add(idRaw to Uri.withAppendedPath(base, idRaw))
                }
            }
            DeleteOutcome(ok, need)
        }

    fun openInput(uri: Uri): java.io.InputStream? = runCatching {
        contentResolver.openInputStream(uri)
    }.getOrNull()

    suspend fun compressInto(
        sourceUri: Uri,
        targetDir: java.io.File,
        displayName: String,
        qualityPercent: Int
    ): Long = withContext(Dispatchers.IO) {
        val bytes = openInput(sourceUri)?.use { it.readBytes() } ?: return@withContext -1
        val firstSize = BitmapFactory.Options().apply { inJustDecodeBounds = true }.let {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it)
            Pair(it.outWidth, it.outHeight)
        }
        val (w, h) = firstSize
        if (w <= 0 || h <= 0) return@withContext -1
        var sample = 1
        val maxDim = 4096
        var newW = w
        var newH = h
        while (newW / 2 >= 1 && newH / 2 >= 1 && newW * newH / (sample * sample) > maxDim * maxDim) {
            sample *= 2
            newW = w / sample
            newH = h / sample
        }
        val dOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dOpts) ?: return@withContext -1
        val base = displayName.substringBeforeLast('.', "photo")
        val outName = "$base s=$qualityPercent.jpg"
        val outFile = File(targetDir, outName)
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, qualityPercent, fos)
        }
        bmp.recycle()
        outFile.length()
    }

    fun createOutputCollection(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            ),
            "SwipewipeRebuild"
        )
        if (!dir.isDirectory) dir.mkdirs()
        return dir
    }
}
