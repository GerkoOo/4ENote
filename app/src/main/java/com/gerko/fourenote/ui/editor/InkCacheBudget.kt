package com.gerko.fourenote.ui.editor

import android.app.ActivityManager
import android.content.Context
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One memory budget for every page's ink picture (see InkBitmapCache), shared by the pages that
 * hold one, so zooming in with several pages on screen can't use up a small tablet's memory.
 */
class InkCacheBudget(private val totalPixels: Float) {
    private var pages = 0

    @Synchronized
    fun register() {
        pages++
    }

    @Synchronized
    fun unregister() {
        pages = (pages - 1).coerceAtLeast(0)
    }

    /** How many pixels one page's picture may have now. */
    @Synchronized
    fun pixelsPerPage(): Float = minOf(MAX_PAGE_PIXELS, totalPixels / pages.coerceAtLeast(1))

    companion object {
        /** The most one page ever gets: about 64 MB. */
        const val MAX_PAGE_PIXELS = 16_000_000f

        @Volatile
        private var shared: InkCacheBudget? = null

        /** A quarter of the app's memory class, at 4 bytes a pixel. */
        fun forMemoryClass(megabytes: Int) = InkCacheBudget(megabytes * 1024f * 1024f / 4f / 4f)

        fun of(context: Context): InkCacheBudget = shared ?: synchronized(this) {
            shared ?: forMemoryClass(context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128)
                .also { shared = it }
        }
    }
}

private val SQRT2 = sqrt(2f)

/**
 * [resolution] snapped down to a step of √2 (1, 1.41, 2, 2.83, 4…), so pinching repaints the
 * picture at a new size only when it passes a step, not on every frame. Never below 1.
 */
internal fun snappedResolution(resolution: Float): Float {
    if (resolution <= 1f) return 1f
    // The small allowance keeps an exact step (2.0) from rounding down to the one below.
    val steps = floor(ln(resolution) / ln(SQRT2) + 1e-4f)
    return SQRT2.pow(steps)
}
