package fr.guim.cinedoury.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What a part of a video is, as the server marks it (`/MediaSegments`, Intro Skipper). */
enum class SegmentType {
    Intro,
    Outro,
    Recap,
    Preview,
    Commercial,
    Unknown,
}

/** A part of a video between [start] and [end]. */
data class MediaSegment(
    val type: SegmentType,
    val start: Duration,
    val end: Duration,
) {
    operator fun contains(position: Duration): Boolean = position >= start && position < end
}

/** A chapter of a video, from its [start]. */
data class Chapter(
    val start: Duration,
    val name: String? = null,
)

/**
 * The preview pictures of a version (trickplay): [thumbnailCount] pictures of [width] × [height],
 * one every [interval], laid in tiles of [tileColumns] × [tileRows] pictures.
 */
data class TrickplayInfo(
    val width: Int,
    val height: Int,
    val tileColumns: Int,
    val tileRows: Int,
    val thumbnailCount: Int,
    val interval: Duration,
) {
    /** The picture shown at [position]: its tile, its column and its row in the tile. */
    fun frameAt(position: Duration): TrickplayFrame? {
        val perTile = tileColumns * tileRows
        if (thumbnailCount <= 0 || !interval.isPositive() || perTile <= 0) return null
        val index = (position / interval).toInt().coerceIn(0, thumbnailCount - 1)
        val cell = index % perTile
        return TrickplayFrame(tile = index / perTile, column = cell % tileColumns, row = cell / tileColumns)
    }
}

data class TrickplayFrame(
    val tile: Int,
    val column: Int,
    val row: Int,
)

/** What to do with a segment (`FONCTIONNALITES.md` § 5, « Pendant la lecture »). */
enum class SegmentAction {
    /** « Passer l'intro », focused, until the end of the segment. */
    Button,

    /** Jump over it at once. */
    Skip,

    /** Nothing. */
    Nothing,
}

/**
 * The rules of the player (`SPEC.md` § 4.11).
 *
 * « Vu »: at [watchedThreshold] of the runtime (90 % by default), or once the credits (the Outro
 * segment) have begun.
 */
object PlaybackRules {
    const val DEFAULT_WATCHED_THRESHOLD = 0.9f

    /** Without an Outro segment, the credits are the last 30 seconds. */
    val CREDITS_WITHOUT_SEGMENT = 30.seconds

    /** The countdown of « Épisode suivant ». */
    val NEXT_COUNTDOWN = 10.seconds

    /** The segments a button or a jump can skip: the credits lead to the next episode. */
    val SKIPPABLE = setOf(SegmentType.Intro, SegmentType.Recap, SegmentType.Preview, SegmentType.Commercial)

    fun isWatched(
        position: Duration,
        runtime: Duration,
        segments: List<MediaSegment> = emptyList(),
        watchedThreshold: Float = DEFAULT_WATCHED_THRESHOLD,
    ): Boolean {
        if (!runtime.isPositive()) return false
        val outro = segments.outro()
        return position >= runtime * watchedThreshold.toDouble() || (outro != null && position >= outro.start)
    }

    /** Where the credits begin: the Outro segment, else 30 s before the end. */
    fun creditsStart(
        runtime: Duration,
        segments: List<MediaSegment>,
    ): Duration = segments.outro()?.start ?: (runtime - CREDITS_WITHOUT_SEGMENT).coerceAtLeast(Duration.ZERO)

    /** « Épisode suivant » shows from the start of the credits to the end, when there is a next one. */
    fun showsNext(
        position: Duration,
        runtime: Duration,
        segments: List<MediaSegment>,
        hasNext: Boolean,
    ): Boolean = hasNext && runtime.isPositive() && position >= creditsStart(runtime, segments) && position < runtime

    /** The skippable segment at [position], if any. */
    fun skippableAt(
        position: Duration,
        segments: List<MediaSegment>,
    ): MediaSegment? = segments.firstOrNull { it.type in SKIPPABLE && position in it }

    private fun List<MediaSegment>.outro(): MediaSegment? = firstOrNull { it.type == SegmentType.Outro }
}

/**
 * « Vous êtes toujours là ? » (`SPEC.md` § 4.11): the episodes played in a row with no key
 * pressed. The one the user starts counts 1; each episode started by the countdown adds 1; any key
 * starts again from 1. When [limit] episodes (3 by default) have played that way, the next one
 * waits for the question instead of starting.
 *
 * @param limit 0 never asks.
 */
class StillWatching(
    private val limit: Int = DEFAULT_LIMIT,
) {
    var inARow: Int = 1
        private set

    /** A key was pressed. */
    fun onInteraction() {
        inARow = 1
    }

    /** Whether the next episode may start on its own; if so, it counts. */
    fun mayAutoplayNext(): Boolean {
        if (limit > 0 && inARow >= limit) return false
        inARow++
        return true
    }

    companion object {
        const val DEFAULT_LIMIT = 3
    }
}
