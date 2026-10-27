package fr.guim.cinedoury.core.designsystem.image

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the images may use on this device (`SPEC.md` § 4.20): the memory and disk caches of Coil
 * and the cards loaded ahead in the next row ([PrefetchImages]). A Chromecast with Google TV (2 GB,
 * 32 bits) gets less than a Shield; a device Android calls « low RAM » less still, and nothing
 * loaded ahead, which would push out of the cache what is on screen.
 */
@Immutable
data class ImageBudget(
    val memoryCacheBytes: Long,
    val diskCacheBytes: Long,
    /** At most this many images of the next row are loaded ahead. */
    val prefetch: Int,
) {
    companion object {
        /**
         * The budget of a device whose heap is [memoryClassMegabytes] (`ActivityManager.memoryClass`)
         * and that Android calls low on memory ([lowRam], `ActivityManager.isLowRamDevice`).
         */
        fun of(
            lowRam: Boolean,
            memoryClassMegabytes: Int,
        ): ImageBudget {
            val heap = memoryClassMegabytes.coerceAtLeast(MIN_HEAP_MB).toLong() * MEGABYTE
            return when {
                lowRam -> {
                    ImageBudget((heap * LOW_RAM_SHARE).toLong(), LOW_RAM_DISK_MB * MEGABYTE, prefetch = 0)
                }

                memoryClassMegabytes < CONSTRAINED_HEAP_MB -> {
                    ImageBudget(
                        (heap * CONSTRAINED_SHARE).toLong(),
                        CONSTRAINED_DISK_MB * MEGABYTE,
                        CONSTRAINED_PREFETCH,
                    )
                }

                else -> {
                    ImageBudget((heap * SHARE).toLong(), DISK_MB * MEGABYTE, PREFETCH)
                }
            }
        }

        /** A device with room: the budget of the screenshots and of the tests. */
        val Default = of(lowRam = false, memoryClassMegabytes = DEFAULT_HEAP_MB)

        private const val MEGABYTE = 1024L * 1024L
        private const val MIN_HEAP_MB = 64
        private const val DEFAULT_HEAP_MB = 256

        /** Below this heap, a device is short of memory even if Android does not say it. */
        private const val CONSTRAINED_HEAP_MB = 192

        /** Share of the heap for decoded images: Coil's 20 % by default, 15 % on a low-RAM device. */
        private const val SHARE = 0.25
        private const val CONSTRAINED_SHARE = 0.2
        private const val LOW_RAM_SHARE = 0.15

        private const val DISK_MB = 256L
        private const val CONSTRAINED_DISK_MB = 128L
        private const val LOW_RAM_DISK_MB = 64L

        /** What the screen shows of a row, and one more ([PrefetchImages]). */
        private const val PREFETCH = 6
        private const val CONSTRAINED_PREFETCH = 3
    }
}

/** The [ImageBudget] of this device, provided by the app. */
val LocalImageBudget = staticCompositionLocalOf { ImageBudget.Default }
