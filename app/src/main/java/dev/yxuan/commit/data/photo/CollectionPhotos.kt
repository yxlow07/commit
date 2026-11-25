package dev.yxuan.commit.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dev.yxuan.commit.data.db.entity.CollectionItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.math.max

/**
 * Collection photos on this phone. A new or changed photo waits in `pending/{itemId}` until
 * photo sync uploads it; uploaded photos are cached by their Storage key, so a photo replaced on
 * the web (a new key) is downloaded again. Every photo is at most [MAX_SIDE] px on its long side.
 * PNGs (and any image with transparency) stay PNG, so transparent backgrounds survive; everything
 * else, camera shots included, is saved as JPEG. The format is read from the bytes ([isPng]) when
 * uploading, never from the file name.
 */
class CollectionPhotos(private val context: Context) {
    private val root = File(context.filesDir, "collection-photos")
    private val pending = File(root, "pending")
    private val cache = File(root, "cache")

    private val _version = MutableStateFlow(0)
    /** Bumps whenever a photo file appears or changes (saved, uploaded, downloaded), so screens redraw. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun notifyChanged() = _version.update { it + 1 }

    // Named .jpg for history (files already waiting keep working); the real format comes from the bytes.
    fun pendingFile(itemId: String): File = File(pending, "$itemId.jpg")

    fun cachedFile(storageKey: String): File = File(cache, storageKey.replace('/', '_'))

    /** The file to show for [item], or null when it has no photo or it isn't downloaded yet. */
    fun fileFor(item: CollectionItemEntity): File? {
        if (item.photoDirty) pendingFile(item.id).takeIf { it.exists() }?.let { return it }
        return item.photoPath?.let(::cachedFile)?.takeIf { it.exists() }
    }

    /** Saves a picked or captured image as [itemId]'s pending photo. */
    fun saveFromUri(uri: Uri, itemId: String) {
        val bitmap = decode(uri) ?: error("Couldn't read that image")
        val png = context.contentResolver.getType(uri) == "image/png" ||
            runCatching { context.contentResolver.openInputStream(uri)?.use { s -> ByteArray(8).also { s.read(it) }.let(::isPng) } }.getOrNull() == true
        write(bitmap, pendingFile(itemId), png)
        notifyChanged()
    }

    /** Saves image bytes (from a zip) as [itemId]'s pending photo. */
    fun saveFromBytes(bytes: ByteArray, itemId: String) {
        val bitmap = decodeBytes(bytes) ?: error("Not an image")
        write(bitmap, pendingFile(itemId), isPng(bytes))
        notifyChanged()
    }

    /** After an upload: the pending file becomes the cached copy of [storageKey]. */
    fun markUploaded(itemId: String, storageKey: String) {
        val from = pendingFile(itemId)
        val to = cachedFile(storageKey)
        to.parentFile?.mkdirs()
        if (from.exists() && !from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    fun deleteLocal(item: CollectionItemEntity) {
        pendingFile(item.id).delete()
        item.photoPath?.let { cachedFile(it).delete() }
    }

    /** A temporary file for the camera to write into (shared through the FileProvider). */
    fun cameraFile(): File = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "capture.jpg") }

    private fun decode(uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = MAX_SIDE.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { decodeBytes(it.readBytes()) }
        }
    }.getOrNull()

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun write(source: Bitmap, file: File, png: Boolean) {
        val longSide = max(source.width, source.height)
        val bitmap = if (longSide > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else source
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        // PNG is lossless (quality is ignored); JPEG has no alpha, so transparent images go PNG too.
        val format = if (png || bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        tmp.outputStream().use { bitmap.compress(format, QUALITY, it) }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    companion object {
        const val MAX_SIDE = 1280
        const val QUALITY = 85

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /** True when [bytes] start with the PNG signature. */
        fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
    }
}
