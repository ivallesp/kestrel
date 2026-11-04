package app.kestrel.ui.shell

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * The app's one image loader (KestrelApp is Coil's SingletonImageLoader.Factory). It fetches listing photos over
 * OkHttp without cookies, so no eBay session ever travels with a photo request, and keeps them in a disk cache that the
 * notifications read for their large icons (platform ItemPhotos, which looks photos up by URL).
 */
object KestrelImageLoader {
    const val DISK_CACHE_FOLDER = "image_cache"
    const val DISK_CACHE_BYTES = 64L * 1024 * 1024
    private const val MEMORY_CACHE_FRACTION = 0.15

    fun create(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { photoClient() })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_FRACTION).build() }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve(DISK_CACHE_FOLDER).toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }.build()

    private fun photoClient(): OkHttpClient = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()
}
