package fr.guim.cinedoury.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import fr.guim.cinedoury.core.model.MarginBudget
import fr.guim.cinedoury.core.model.MarginPlan

/**
 * How much of the film the player keeps ahead (`SPEC.md` § 4.11): ExoPlayer starts with
 * [startMillis] loaded, starts again after a stall with [rebufferMillis], and keeps between [minMillis]
 * and [maxMillis] ahead, up to [targetBytes] in memory.
 *
 * **The bytes are on the Java heap** (R, `SPEC.md` § 4.29): Media3's `DefaultAllocator` keeps them in
 * `byte[]`. The TV once asked for 192 MB whatever the device, the whole heap of a Shield
 * (`dalvik.vm.heapgrowthlimit` = 192m): a 4K film at 25 Mb/s, played directly on the local network,
 * filled it in seconds and the app died of an `OutOfMemoryError`. [tv] is now bounded by the heap of
 * the process, like [phone] (M2, `SPEC.md` § 4.27), which then lets the disk stage carry the margin.
 */
data class PlayerBuffer(
    val minMillis: Int,
    val maxMillis: Int,
    val startMillis: Int,
    val rebufferMillis: Int,
    val targetBytes: Int,
) {
    companion object {
        private const val MEGABYTE = 1024 * 1024
        private const val START_MILLIS = 1_500
        private const val REBUFFER_MILLIS = 4_000

        /** The share of the heap the buffer of the TV may take: 40 %, the rest is the app's. */
        const val TV_HEAP_PERCENT = 40

        /** Never less: about 10 s at 25 Mb/s. */
        const val TV_MIN_BYTES = 32 * MEGABYTE

        /** Never more: what the TV asked for until R, enough for 60 s at 25 Mb/s. */
        const val TV_MAX_BYTES = 192 * MEGABYTE

        /**
         * The TV: 30 to 90 s ahead, as since the J4, but at most [TV_HEAP_PERCENT] % of [heapBytes]
         * (`Runtime.maxMemory()`, the heap the process may grow to): 76.8 MB on a heap of 192 MB.
         */
        fun tv(heapBytes: Long) =
            PlayerBuffer(
                minMillis = 30_000,
                maxMillis = 90_000,
                startMillis = START_MILLIS,
                rebufferMillis = REBUFFER_MILLIS,
                targetBytes =
                    (heapBytes * TV_HEAP_PERCENT / 100)
                        .coerceIn(
                            TV_MIN_BYTES.toLong(),
                            TV_MAX_BYTES.toLong(),
                        ).toInt(),
            )

        /** The memory of the phone's margin: up to 2 min, at most [MarginBudget.memoryBytes] of its heap. */
        fun phone(memoryClassMegabytes: Int) =
            PlayerBuffer(
                minMillis = MarginPlan.LOW_MEMORY.inWholeMilliseconds.toInt(),
                maxMillis = PHONE_MEMORY_MILLIS,
                startMillis = START_MILLIS,
                rebufferMillis = REBUFFER_MILLIS,
                targetBytes = MarginBudget.memoryBytes(memoryClassMegabytes),
            )

        private const val PHONE_MEMORY_MILLIS = 120_000
    }

    /**
     * The `LoadControl` of ExoPlayer: the bytes win over the durations
     * (`setPrioritizeTimeOverSizeThresholds(false)`). Read in Media3 1.11.1: loading stops at
     * [targetBytes] even below [minMillis] (`shouldContinueLoading`), and playback starts, or starts
     * again, as soon as [targetBytes] are reached, before [startMillis] or [rebufferMillis]
     * (`shouldStartPlayback`): a very high bitrate never blocks at the cap.
     */
    @OptIn(UnstableApi::class)
    fun loadControl(allocator: DefaultAllocator = allocator()): DefaultLoadControl =
        DefaultLoadControl
            .Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(minMillis, maxMillis, startMillis, rebufferMillis)
            .setTargetBufferBytes(targetBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

    /** Media3's own: 64 KB blocks, trimmed when the player resets. */
    @OptIn(UnstableApi::class)
    fun allocator(): DefaultAllocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
}

/**
 * Wraps the source of the bytes of a stream (M1): none on the TV; M2 puts a `CacheDataSource` over it
 * on the phone, a buffer on disk. The headers of the stream (Jellyfin: `Authorization`) stay on the
 * HTTP source it is given.
 */
fun interface DataSources {
    fun wrap(upstream: DataSource.Factory): DataSource.Factory

    companion object {
        val Direct = DataSources { it }
    }
}
