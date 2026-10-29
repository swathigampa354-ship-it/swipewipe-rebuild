package com.swipewipe.rebuild.graph

import android.net.Uri
import com.swipewipe.rebuild.db.AppDatabase
import com.swipewipe.rebuild.db.CloudMediaItemEntity
import com.swipewipe.rebuild.db.CloudMonthAggregateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset

class GraphManager(private val context: android.content.Context) {

    private val tokens = GraphTokenStore(context)
    private val http = okhttp3.OkHttpClient.Builder().build()

    fun isSignedIn(): Boolean = tokens.access() != null
    fun account(): String = tokens.account() ?: ""
    fun signOut() = tokens.clear()

    suspend fun ensureAccessToken(clientId: String): String = withContext(Dispatchers.IO) {
        if (clientId.isEmpty()) throw IllegalStateException("Set your Microsoft app client id in Settings first")
        val now = System.currentTimeMillis() / 1000L
        val access = tokens.access()
        if (access != null && tokens.expiresAt() > now + 120) return@withContext access
        val refresh = tokens.refresh() ?: throw IllegalStateException("not signed in")
        val fresh = GraphAuth.refreshToken(clientId, refresh)
        tokens.save(
            fresh.accessToken, fresh.refreshToken,
            now + fresh.expiresInSeconds, fresh.account.ifEmpty { tokens.account() ?: "" }
        )
        fresh.accessToken
    }

    suspend fun beginSignIn(clientId: String): String {
        val (v, ch) = GraphAuth.pkce
        AuthState.pendingVerifier = v
        return GraphAuth.authorizeUrl(clientId, ch)
    }

    suspend fun handleRedirect(uri: Uri, clientId: String): Boolean = withContext(Dispatchers.IO) {
        if (!uri.toString().startsWith("${GraphAuth.REDIRECT_SCHEME}://")) return@withContext false
        try {
            val error = uri.getQueryParameter("error")
            if (error != null) {
                throw IllegalStateException(
                    uri.getQueryParameter("error_description") ?: error
                )
            }
            val code = uri.getQueryParameter("code") ?: return@withContext false
            val verifier = AuthState.pendingVerifier
                ?: throw IllegalStateException("no pending sign-in state")
            val fresh = GraphAuth.exchangeCode(clientId, code, verifier)
            tokens.save(
                fresh.accessToken, fresh.refreshToken,
                System.currentTimeMillis() / 1000L + fresh.expiresInSeconds,
                fresh.account
            )
            true
        } finally {
            AuthState.pendingVerifier = null
        }
    }

    private object AuthState {
        @Volatile
        var pendingVerifier: String? = null
    }

    data class CloudPhoto(
        val id: String,
        val name: String,
        val size: Long,
        val modifiedMs: Long,
        val isVideo: Boolean,
        val monthKey: String
    )

    class GraphHttpException(val code: Int, message: String) : RuntimeException(message)

    suspend fun listOneDrivePhotos(
        clientId: String,
        maxPages: Int = 20
    ): List<CloudPhoto> = withContext(Dispatchers.IO) {
        val token = ensureAccessToken(clientId)
        var nextLink: String? = graphUri("?\$top=200")
        val photos = ArrayList<CloudPhoto>(512)
        var pages = 0
        while (nextLink != null && pages < maxPages) {
            val url = nextLink
            nextLink = null
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw GraphHttpException(resp.code, resp.message ?: "graph error")
                val body = JSONObject(resp.body?.string() ?: "{}")
                val items = body.optJSONArray("value") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    if (o.optJSONObject("file") == null) continue
                    val modified = parseGraphTime(o.optString("lastModifiedDateTime"))
                    photos.add(
                        CloudPhoto(
                            id = o.getString("id"),
                            name = o.optString("name", "drive_item"),
                            size = o.optLong("size", 0),
                            modifiedMs = modified,
                            isVideo = o.optJSONObject("video") != null,
                            monthKey = monthKeyOf(modified)
                        )
                    )
                }
                nextLink = body.optString("@odata.nextLink").takeIf { it.isNotEmpty() }
            }
            pages++
        }
        photos
    }

    private fun graphUri(query: String): String =
        "https://graph.microsoft.com/v1.0/me/drive/special/photos/children$query"

    fun monthKeyOf(ms: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)
        return "%04d-%02d".format(d.year, d.monthValue)
    }

    private fun parseGraphTime(s: String): Long = try {
        java.time.Instant.parse(s).toEpochMilli()
    } catch (t: Throwable) {
        0L
    }

    fun aggregateMonths(photos: List<CloudPhoto>): List<CloudMonthAggregateEntity> =
        photos.groupBy { it.monthKey }.map { (key, list) ->
            CloudMonthAggregateEntity(
                mediaSource = "onedrive",
                monthKey = key,
                itemCount = list.size,
                totalSize = list.sumOf { it.size }
            )
        }.sortedByDescending { it.monthKey }

    suspend fun insertIntoDb(db: AppDatabase, photos: List<CloudPhoto>) =
        db.appDao().upsertAll(
            photos.map {
                CloudMediaItemEntity(
                    id = it.id,
                    mediaSource = "onedrive",
                    name = it.name,
                    displayUri = null,
                    sizeBytes = it.size,
                    lastModifiedMs = it.modifiedMs,
                    isVideo = it.isVideo,
                    monthKey = it.monthKey
                )
            }
        )

    suspend fun deleteItemsBatch(clientId: String, ids: List<String>): Map<String, Boolean> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext java.util.Collections.emptyMap<String, Boolean>()
            val token = ensureAccessToken(clientId)
            val boundary = "batch_" + java.util.UUID.randomUUID()
            val sb = StringBuilder()
            ids.forEach { id ->
                sb.append("--").append(boundary).append("\r\n")
                sb.append("Content-Type: application/http\r\n")
                sb.append("Content-Transfer-Encoding: binary\r\n\r\n")
                sb.append("DELETE /v1.0/me/drive/items/").append(id).append(" HTTP/1.1\r\nAccept: application/json\r\n\r\n")
            }
            sb.append("--").append(boundary).append("--\r\n")
            val body = okhttp3.RequestBody.create(
                "multipart/mixed; boundary=$boundary".toMediaTypeOrNull(),
                sb.toString()
            )
            val req = okhttp3.Request.Builder()
                .url("https://graph.microsoft.com/v1.0/\$batch")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw GraphHttpException(resp.code, resp.message ?: "graph error")
                val json = JSONObject(resp.body?.string() ?: "{}")
                val out = LinkedHashMap<String, Boolean>()
                val responses = json.optJSONArray("responses") ?: return@use out
                for (i in 0 until responses.length()) {
                    val r = responses.getJSONObject(i)
                    val idPart = r.optString("id", "")
                    out[idPart] = r.optInt("status", 0) in 200 until 300
                }
                if (out.isEmpty() && ids.isNotEmpty()) {
                    ids.forEach { out[it] = false }
                }
                out
            }
        }
}
