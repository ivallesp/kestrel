package app.kestrel.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.util.Log
import android.util.LruCache
import coil3.SingletonImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Item photos for notification large icons. Never fetches: a photo is used only if the app already has it. */
internal interface ItemPhotos {
    /** A photo already decoded in memory, or null. Cheap enough for the main thread. */
    fun cached(url: String?): Bitmap?

    /** Decodes a photo the app has already downloaded, or returns null. Never touches the network. */
    suspend fun load(url: String): Bitmap?
}

/**
 * Reads the photo from the image loader's disk cache (the copy the UI downloaded when the listing was shown) and
 * cuts it to a square, as large icons that do not show a person must be.
 */
internal class DiskCachedItemPhotos(
    private val context: Context,
) : ItemPhotos {
    private val memory = LruCache<String, Bitmap>(MEMORY_ENTRIES)

    override fun cached(url: String?): Bitmap? = url?.let { memory.get(it) }

    override suspend fun load(url: String): Bitmap? {
        memory.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val diskCache = SingletonImageLoader.get(context).diskCache ?: return@withContext null
                val photo =
                    diskCache.openSnapshot(url)?.use { snapshot ->
                        decodeSquare(snapshot.data.toFile().path)
                    }
                photo?.also { memory.put(url, it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not read a cached item photo", error)
                null
            }
        }
    }

    private fun decodeSquare(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= ICON_SIZE_PIXELS) sampleSize *= 2
        val decoded =
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null
        return ThumbnailUtils.extractThumbnail(
            decoded,
            ICON_SIZE_PIXELS,
            ICON_SIZE_PIXELS,
            ThumbnailUtils.OPTIONS_RECYCLE_INPUT,
        )
    }

    private companion object {
        const val TAG = "ItemPhotos"
        const val MEMORY_ENTRIES = 8
        const val ICON_SIZE_PIXELS = 256
    }
}
