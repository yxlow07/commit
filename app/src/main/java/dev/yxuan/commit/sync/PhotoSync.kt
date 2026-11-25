package dev.yxuan.commit.sync

import android.util.Log
import dev.yxuan.commit.data.db.AppDatabase
import dev.yxuan.commit.data.photo.CollectionPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Collection photos ↔ Supabase Storage (private bucket [BUCKET], objects under `{user_id}/`), over
 * Storage's REST API with the signed-in user's token, so the bucket's policies apply.
 *
 * - Upload: each new or changed photo goes up as `{user_id}/{item_id}-{time}.jpg` (or `.png`) (a new key per
 *   version, so other devices notice the change); the row then gets that key and is pushed.
 * - Download: uploaded photos missing on this phone are fetched into the cache.
 * A failed photo is retried on the next sync; it never fails the row sync.
 */
class PhotoSync(
    private val db: AppDatabase,
    private val photos: CollectionPhotos,
    private val baseUrl: String,
    private val anonKey: String,
) {
    /** Returns how many rows changed (uploads give their item a new photo key to push). */
    suspend fun sync(accessToken: String, userId: String): Int = withContext(Dispatchers.IO) {
        val dao = db.collectionDao()
        var changed = 0
        for (item in dao.getPhotosToUpload()) {
            val file = photos.pendingFile(item.id)
            if (!file.exists()) continue
            val stamp = file.lastModified() to file.length()
            val bytes = file.readBytes()
            val png = CollectionPhotos.isPng(bytes)
            val key = "$userId/${item.id}-${System.currentTimeMillis()}.${if (png) "png" else "jpg"}"
            val ok = runCatching { request("POST", "object/$BUCKET/${encode(key)}", accessToken, bytes, if (png) "image/png" else "image/jpeg") }
                .onFailure { Log.w(TAG, "Photo upload failed for ${item.id}", it) }.getOrDefault(false)
            if (!ok) continue
            // Replaced while uploading (a re-import or an edit): leave it pending, so the new photo
            // goes up next round instead of being filed under this upload's key and lost.
            if (file.lastModified() to file.length() != stamp) continue
            photos.markUploaded(item.id, key)
            dao.setUploaded(item.id, key, System.currentTimeMillis())
            changed++
        }
        for (item in dao.getUploadedPhotos()) {
            val key = item.photoPath ?: continue
            if (item.photoDirty) continue
            val target = photos.cachedFile(key)
            if (target.exists()) continue
            runCatching {
                val bytes = download("object/authenticated/$BUCKET/${encode(key)}", accessToken) ?: return@runCatching
                target.parentFile?.mkdirs()
                val tmp = java.io.File(target.parentFile, target.name + ".part")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
                photos.notifyChanged()
            }.onFailure { Log.w(TAG, "Photo download failed for ${item.id}", it) }
        }
        changed
    }

    private fun encode(key: String) = key.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun open(method: String, path: String, token: String): HttpURLConnection =
        (URL("${baseUrl.trimEnd('/')}/storage/v1/$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("apikey", anonKey)
        }

    private fun request(method: String, path: String, token: String, body: ByteArray, contentType: String): Boolean {
        val c = open(method, path, token)
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            c.setRequestProperty("x-upsert", "true")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code !in 200..299) Log.w(TAG, "Storage $method $path: $code ${c.errorStream?.bufferedReader()?.readText()?.take(300)}")
            return code in 200..299
        } finally {
            c.disconnect()
        }
    }

    private fun download(path: String, token: String): ByteArray? {
        val c = open("GET", path, token)
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Storage GET $path: $code")
                return null
            }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val BUCKET = "collection-photos"
        private const val TAG = "CommitPhotos"
    }
}
