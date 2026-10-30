package fr.guim.cinedoury.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One piece of the film the margin is made of (M2, `SPEC.md` § 4.27): a segment of a transcoded stream
 * (HLS), or a range of bytes of a file played as it is, with the part of the film it carries.
 */
data class MarginChunk(
    val start: Duration,
    val end: Duration,
)

/**
 * What the player says of itself, for the plan of the margin.
 *
 * @param memoryEnd where what the player holds in memory ends (ExoPlayer's buffered position).
 * @param playerLoading the player is loading itself, from the chunk at [memoryEnd].
 * @param started the film has started playing: nothing is fetched ahead before.
 */
data class MarginInputs(
    val position: Duration,
    val memoryEnd: Duration,
    val playerLoading: Boolean,
    val started: Boolean,
)

/**
 * The margin of the phone (M2, `SPEC.md` § 4.27): what is loaded ahead of the position, in two stages.
 * The memory of the player (about 2 min, capped in bytes) comes first; the disk (up to the margin
 * chosen, 5, 15 or 30 min) follows, filled chunk after chunk from where the memory ends, so that what
 * is loaded is always one piece from the position, without holes.
 */
object MarginPlan {
    /**
     * The fetch waits while the memory of the player is under this and the disk has nothing beyond it:
     * the player loads from the network then, and the margin gives way to it.
     */
    val LOW_MEMORY = 30.seconds

    /** What the disk must hold beyond the memory for the player to refill from it, not the network. */
    val DISK_AHEAD = 20.seconds

    /** The margins offered (« Marge de lecture »), in minutes; [DEFAULT_MINUTES] unless chosen. */
    val MINUTES = listOf(5, 15, 30)
    const val DEFAULT_MINUTES = 15

    /** The chunk that holds [time]: the last one starting at or before it; 0 before the first. */
    fun indexAt(
        chunks: List<MarginChunk>,
        time: Duration,
    ): Int {
        var low = 0
        var high = chunks.lastIndex
        var found = 0
        while (low <= high) {
            val middle = (low + high) / 2
            if (chunks[middle].start <= time) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }

    /**
     * Where the margin ends: the memory of the player, then every chunk cached on the disk that follows
     * without a hole. A chunk away from the position (left by a seek) does not count until the margin
     * reaches it.
     */
    fun end(
        chunks: List<MarginChunk>,
        cached: (Int) -> Boolean,
        position: Duration,
        memoryEnd: Duration,
    ): Duration {
        val memory = maxOf(position, memoryEnd)
        if (chunks.isEmpty()) return memory
        var end = memory
        for (index in indexAt(chunks, position)..chunks.lastIndex) {
            val chunk = chunks[index]
            if (chunk.end > memory && !cached(index)) break
            end = maxOf(end, chunk.end)
        }
        return end
    }

    /**
     * The next chunk to fetch to the disk, or null: nothing before the film starts, nothing while the
     * player loads its memory from the network (the margin gives way to it), nothing beyond [target],
     * nothing when the disk is full ([room] false). The player's own chunk (the one it is loading) is
     * left to it.
     */
    @Suppress("ReturnCount") // Each reason to fetch nothing now.
    fun next(
        chunks: List<MarginChunk>,
        cached: (Int) -> Boolean,
        inputs: MarginInputs,
        target: Duration,
        room: Boolean = true,
    ): Int? {
        if (!inputs.started || !room || chunks.isEmpty()) return null
        val position = inputs.position
        val memoryEnd = maxOf(position, inputs.memoryEnd)
        val end = end(chunks, cached, position, memoryEnd)
        if (end - position >= target || end >= chunks.last().end) return null
        val memory = memoryEnd - position
        if (memory < LOW_MEMORY && end - memoryEnd < DISK_AHEAD) return null
        val first =
            if (inputs.playerLoading) {
                chunks.indexOfFirst { it.start >= memoryEnd }.takeIf { it >= 0 } ?: return null
            } else {
                indexAt(chunks, memoryEnd)
            }
        for (index in first..chunks.lastIndex) {
            val chunk = chunks[index]
            if (chunk.start >= position + target) return null
            if (chunk.end > memoryEnd && !cached(index)) return index
        }
        return null
    }
}

/**
 * The margin as the screen shows it: on the bar (from the position to [end]) and on the information
 * screen (« Marge : 12 min (dont 2 en mémoire) », the bandwidth measured).
 *
 * @param bandwidth what the fetch to the disk measured, in bits per second; null before a chunk.
 * @param offline the last fetches failed: the network is down, the film plays on the margin.
 */
data class MarginStatus(
    val position: Duration,
    val end: Duration,
    val memoryEnd: Duration,
    val target: Duration,
    val bandwidth: Long? = null,
    val offline: Boolean = false,
    val fetching: Boolean = false,
) {
    val ahead: Duration get() = (end - position).coerceAtLeast(Duration.ZERO)

    val inMemory: Duration get() = (memoryEnd - position).coerceIn(Duration.ZERO, ahead)

    val full: Boolean get() = ahead >= target - FULL_TOLERANCE

    private companion object {
        val FULL_TOLERANCE = 30.seconds
    }
}

/** How much memory and disk the margin may take on this device (`SPEC.md` § 4.27). */
object MarginBudget {
    private const val MEGABYTE = 1024 * 1024
    private const val GIGABYTE = 1024L * MEGABYTE

    /** A quarter of the Java heap of the app, from 32 to 128 MB: about 2 min at 4 Mb/s. */
    fun memoryBytes(memoryClassMegabytes: Int): Int =
        (memoryClassMegabytes * MEGABYTE / MEMORY_SHARE).coerceIn(MIN_MEMORY * MEGABYTE, MAX_MEMORY * MEGABYTE)

    /**
     * Half of the free space once 1 GB is left to the device, at most 4 GB: 30 min at 16 Mb/s. The
     * margin stops short of it rather than evict what is ahead.
     */
    fun diskBytes(usableBytes: Long): Long =
        ((usableBytes - RESERVE * GIGABYTE) / 2).coerceIn(0, MAX_DISK * GIGABYTE)

    /** The margin [bytes] carry at [bitrate] (bits per second). */
    fun duration(
        bytes: Long,
        bitrate: Long,
    ): Duration = if (bitrate <= 0) Duration.INFINITE else (bytes * BITS * MILLIS / bitrate).milliseconds

    private const val MEMORY_SHARE = 4
    private const val MIN_MEMORY = 32
    private const val MAX_MEMORY = 128
    private const val RESERVE = 1L
    private const val MAX_DISK = 4L
    private const val BITS = 8
    private const val MILLIS = 1_000

    /** The margin target when nothing is chosen. */
    val DEFAULT_TARGET = MarginPlan.DEFAULT_MINUTES.minutes
}
