package io.github.krank56.webmote.ui.apps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory cache of the TV's app icons, keyed by URL (which includes the TV's address, so TVs
 * don't share entries). Icons are fetched through the session, which trusts only the TV's pinned
 * certificate.
 */
object AppIconCache {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val RETRY_AFTER_MS = 30_000L

    private val icons = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** When each icon last failed to load, so a scrolling grid doesn't refetch it on every pass. */
    private val failures = ConcurrentHashMap<String, Long>()

    fun cached(url: String): Bitmap? = icons.get(url)

    /** Returns the icon at [url], loading it with [load] if it isn't cached; null if it can't be loaded. */
    suspend fun get(url: String, targetPx: Int, load: suspend (String) -> ByteArray?): Bitmap? {
        icons.get(url)?.let { return it }
        val failedAt = failures[url]
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < RETRY_AFTER_MS) return null
        val bitmap = load(url)?.let { bytes -> withContext(Dispatchers.Default) { decode(bytes, targetPx) } }
        if (bitmap == null) {
            failures[url] = SystemClock.elapsedRealtime()
        } else {
            failures.remove(url)
            icons.put(url, bitmap)
        }
        return bitmap
    }

    private fun decode(bytes: ByteArray, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
