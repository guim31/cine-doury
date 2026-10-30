package fr.guim.cinedoury.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import fr.guim.cinedoury.core.model.MarginChunk
import fr.guim.cinedoury.core.model.MarginInputs
import fr.guim.cinedoury.core.model.MarginPlan
import fr.guim.cinedoury.core.model.MarginStatus
import fr.guim.cinedoury.core.source.FailureLog
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Fills the disk stage of the margin (M2, `SPEC.md` § 4.27), on a thread of its own: chunk after chunk
 * from where the memory of the player ends ([MarginPlan.next]), each one written to the [PlaybackCache]
 * by a [CacheWriter] through the same source as the player's, so that the player reads it back from
 * the disk when it gets there, network or not. A transcoded stream is fetched segment by segment from
 * its playlist (which Jellyfin publishes whole), a file as it is by ranges of bytes.
 *
 * A failure (the network is down) is tried again, sooner and sooner, and at once when the network
 * changes; the status says « offline » meanwhile. What lies more than [KEEP_BEHIND] behind the
 * position is removed from the disk, and all of it at [release].
 */
@OptIn(UnstableApi::class)
internal class MarginLoader(
    private val disk: PlaybackCache,
    upstream: DataSource.Factory,
    private val network: NetworkMonitor,
    private val target: Duration,
    private val prefix: String,
) {
    /** The stream played: one more at each [newStream]; what the previous ones wrote is removed. */
    @Volatile private var generation = 0

    @Volatile private var removedUpTo = 0

    /**
     * Every key of this playback starts with [prefix] (its end removes them, and no other), then the
     * stream it belongs to.
     */
    val keys = CacheKeyFactory { spec -> "$prefix$generation|" + (spec.key ?: spec.uri.toString()) }

    /** What the fetch writes through: the network, written to the disk. */
    private val writers: CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(disk.cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(keys)

    /**
     * What the player reads through: the disk where the margin is, else the network. The player never
     * writes: a file played as it is keeps its connection open while its memory is full, and would hold
     * the lock of the rest of the file against the fetch. What it loads is in its memory anyway, and the
     * margin starts where the memory ends.
     */
    val dataSources: CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(disk.cache)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory(keys)
            .setCacheWriteDataSinkFactory(null)

    /** The chunks of the stream played, with what to fetch for each. */
    class Source(
        val id: Int,
        val chunks: List<MarginChunk>,
        val specs: List<DataSpec>,
        /** HLS: each spec is a whole segment, of a length the disk learns as it writes it. */
        val segments: Boolean,
        /** The initialisation segment of fMP4 segments, fetched first. */
        val init: DataSpec? = null,
    )

    @Volatile private var source: Source? = null

    @Volatile private var inputs: MarginInputs? = null

    @Volatile private var running = true

    @Volatile private var writer: CacheWriter? = null

    @Volatile private var failures = 0

    @Volatile private var fetching = false

    @Volatile private var bandwidth: Long? = null

    private val known = ConcurrentHashMap.newKeySet<Int>()

    @Volatile private var trimmedTo = 0
    private val lock = Object()
    private val networkChanges = network.onChange { wake() }
    private val thread = Thread(::loop, "CineDoury:margin").apply { isDaemon = true }

    init {
        thread.start()
    }

    /** Another stream is played (another quality, the fallback): what the last one wrote goes. */
    fun newStream() {
        generation++
        source = null
        writer?.cancel()
        wake()
    }

    /** From the main thread, after each look at the player: what is played, and where. */
    fun update(
        source: Source?,
        inputs: MarginInputs,
    ) {
        if (source?.id != this.source?.id) {
            known.clear()
            trimmedTo = 0
            writer?.cancel()
        }
        this.source = source
        this.inputs = inputs
        wake()
    }

    /** The margin now, for the bar and the information screen. */
    fun status(
        position: Duration,
        memoryEnd: Duration,
    ): MarginStatus {
        val current = source
        val end =
            if (current == null) {
                maxOf(position, memoryEnd)
            } else {
                MarginPlan.end(current.chunks, { cached(current, it) }, position, memoryEnd)
            }
        return MarginStatus(
            position = position,
            end = end,
            memoryEnd = maxOf(position, memoryEnd),
            target = target,
            bandwidth = bandwidth,
            offline = failures > 0 || !network.state.value.online,
            fetching = fetching,
        )
    }

    /** Stops the fetch and empties the disk of this playback, on the thread of the loader. */
    fun release() {
        running = false
        networkChanges.close()
        writer?.cancel()
        wake()
    }

    private fun wake() = synchronized(lock) { lock.notifyAll() }

    private fun pause(millis: Long) = synchronized(lock) { if (running) lock.wait(millis) }

    private fun loop() {
        try {
            while (running) {
                try {
                    step()
                } catch (error: IllegalStateException) {
                    // The disk refused (full, removed under it): the film plays on its memory.
                    FailureLog.warn("Margin: the disk failed", error)
                    pause(MAX_RETRY_MILLIS)
                }
            }
        } finally {
            runCatching { disk.clear(prefix) }
        }
    }

    private fun step() {
        removeOldStreams()
        val current = source
        val now = inputs
        val index = if (current == null || now == null) null else next(current, now)
        if (current == null || now == null || index == null) {
            if (current != null && now != null) trim(current, now.position)
            pause(IDLE_MILLIS)
        } else {
            fetch(current, index)
        }
    }

    private fun removeOldStreams() {
        val latest = generation
        if (removedUpTo >= latest) return
        for (old in removedUpTo until latest) disk.clear("$prefix$old|")
        removedUpTo = latest
    }

    private fun next(
        current: Source,
        now: MarginInputs,
    ): Int? {
        val room = disk.cache.cacheSpace + CHUNK_ROOM < disk.budget()
        return MarginPlan.next(current.chunks, { cached(current, it) }, now, target, room)
    }

    private fun fetch(
        current: Source,
        index: Int,
    ) {
        fetching = true
        val started = System.nanoTime()
        var written = 0L
        try {
            current.init?.let { init -> if (!cachedSpec(init, segment = true)) write(init) { written += it } }
            write(current.specs[index]) { written += it }
            // Written whole, or someone else held it: then it is tried again later, as a failure.
            if (!cachedSpec(current.specs[index], current.segments)) throw IOException("Chunk $index not cached")
            if (source?.id == current.id) known += index
            failures = 0
            val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
            // Only what came from the network tells its speed; a few kilobytes say little.
            if (written > MEASURABLE && millis > 0) {
                val measured = written * BITS * MILLIS / millis
                bandwidth = bandwidth?.let { (it * (1 - SMOOTHING) + measured * SMOOTHING).toLong() } ?: measured
            }
        } catch (error: IOException) {
            if (!running || source?.id != current.id) return
            failures++
            if (failures == 1) FailureLog.warn("Margin: fetch failed, the film plays on its margin", error)
            pause((RETRY_MILLIS shl (failures - 1).coerceAtMost(RETRY_STEPS)).coerceAtMost(MAX_RETRY_MILLIS))
        } finally {
            fetching = false
        }
    }

    private fun write(
        spec: DataSpec,
        progress: (Long) -> Unit,
    ) {
        val cacheWriter =
            CacheWriter(writers.createDataSource(), spec, null) { _, _, newBytes -> progress(newBytes) }
        writer = cacheWriter
        try {
            if (running) cacheWriter.cache()
        } finally {
            writer = null
        }
    }

    /** Whether chunk [index] is on the disk, whole. */
    private fun cached(
        current: Source,
        index: Int,
    ): Boolean {
        if (index in known) return true
        return cachedSpec(current.specs[index], current.segments).also { if (it) known += index }
    }

    private fun cachedSpec(
        spec: DataSpec,
        segment: Boolean,
    ): Boolean {
        val key = keys.buildCacheKey(spec)
        val cache = disk.cache
        if (!segment) return cache.isCached(key, spec.position, spec.length)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length >= 0 && cache.isCached(key, 0, length)
    }

    /** Removes what lies more than [KEEP_BEHIND] behind the position: the disk stays for what is ahead. */
    private fun trim(
        current: Source,
        position: Duration,
    ) {
        val behind = position - KEEP_BEHIND
        if (!behind.isPositive()) return
        val until = MarginPlan.indexAt(current.chunks, behind)
        if (until <= trimmedTo) return
        val cache = disk.cache
        if (current.segments) {
            for (index in trimmedTo until until) {
                cache.removeResource(keys.buildCacheKey(current.specs[index]))
                known -= index
            }
        } else {
            val spec = current.specs[until]
            val key = keys.buildCacheKey(spec)
            cache
                .getCachedSpans(key)
                .filter { it.position + it.length <= spec.position }
                .forEach(cache::removeSpan)
            for (index in trimmedTo until until) known -= index
        }
        trimmedTo = until
    }

    companion object {
        /** What stays on the disk behind the position, for a short jump back. */
        val KEEP_BEHIND = 2.minutes

        /** A file played as it is is fetched by ranges of this size at least. */
        const val FILE_CHUNK_BYTES = 4L * 1024 * 1024

        /** At most this many ranges for a file: a large one gets larger ranges. */
        private const val MAX_FILE_CHUNKS = 4_000L
        private const val CHUNK_ROOM = 16L * 1024 * 1024
        private const val IDLE_MILLIS = 500L
        private const val RETRY_MILLIS = 1_000L
        private const val RETRY_STEPS = 3
        private const val MAX_RETRY_MILLIS = 8_000L
        private const val MEASURABLE = 256L * 1024
        private const val SMOOTHING = 0.3
        private const val BITS = 8
        private const val MILLIS = 1_000
        private const val NANOS_PER_MILLI = 1_000_000

        /** The segments of a transcoded stream (a playlist of Jellyfin, published whole). */
        fun segments(
            id: Int,
            playlist: HlsMediaPlaylist,
        ): Source {
            val segments = playlist.segments
            val init =
                segments.firstOrNull()?.initializationSegment?.let { segment(playlist.baseUri, it) }
            return Source(
                id = id,
                chunks =
                    segments.map {
                        val start = it.relativeStartTimeUs.microseconds
                        MarginChunk(start, start + it.durationUs.microseconds)
                    },
                specs = segments.map { segment(playlist.baseUri, it) },
                segments = true,
                init = init,
            )
        }

        private fun segment(
            base: String,
            segment: HlsMediaPlaylist.SegmentBase,
        ): DataSpec =
            DataSpec
                .Builder()
                .setUri(UriUtil.resolveToUri(base, segment.url))
                .setPosition(segment.byteRangeOffset)
                .setLength(segment.byteRangeLength)
                .build()

        /**
         * A file played as it is, by ranges of bytes; the part of the film each carries is read from
         * the average bitrate of the file.
         */
        fun file(
            id: Int,
            uri: Uri,
            size: Long,
            duration: Duration,
        ): Source {
            val chunk = maxOf(FILE_CHUNK_BYTES, (size + MAX_FILE_CHUNKS - 1) / MAX_FILE_CHUNKS)
            val count = ((size + chunk - 1) / chunk).toInt()
            val millis = duration.inWholeMilliseconds.toDouble()

            fun time(offset: Long) = (millis * offset / size).toLong().milliseconds
            return Source(
                id = id,
                chunks = List(count) { MarginChunk(time(it * chunk), time(minOf(size, (it + 1) * chunk))) },
                specs =
                    List(count) {
                        DataSpec
                            .Builder()
                            .setUri(uri)
                            .setPosition(it * chunk)
                            .setLength(minOf(chunk, size - it * chunk))
                            .build()
                    },
                segments = false,
            )
        }

        /** No length known (`C.LENGTH_UNSET`): a file whose size the server did not say. */
        fun hasLength(size: Long?): Boolean = size != null && size > 0 && size != C.LENGTH_UNSET.toLong()
    }
}
