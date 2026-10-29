package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import java.time.Duration
import java.time.Instant

/** Why a title is in the mosaic « Ce soir » (essai réel 5, `SPEC.md` § 4.23): what its tile says. */
enum class TonightKind {
    /** Started, not finished: « REPRENDRE · RESTE 14 MIN ». */
    Resume,

    /** The next episode of a series in progress: « À SUIVRE ». */
    NextUp,

    /** The next episode of a series followed, added lately: « NOUVEL ÉPISODE ». */
    NewEpisode,

    /** Among the latest additions: « AJOUTÉ HIER ». */
    Added,

    /** Never watched, or long ago: « JAMAIS VU », « PAS VU DEPUIS 2 ANS ». */
    Rediscover,
}

/** A tile of the mosaic. */
data class TonightTile(
    val item: MediaItem,
    val kind: TonightKind,
)

/** What the mosaic is made of, as the server gave it (`HomeRows.fetchTonight`). */
data class TonightSources(
    val resume: List<MediaItem> = emptyList(),
    val nextUp: List<MediaItem> = emptyList(),
    val latest: List<MediaItem> = emptyList(),
    val rediscover: List<MediaItem> = emptyList(),
)

/**
 * The mosaic « Ce soir » at the top of the home (essai réel 5): a large tile and four small ones,
 * always full while the libraries hold enough titles, never twice the same title or series.
 */
object Tonight {
    /** The large tile and the four small ones. */
    const val TILES = 5

    /** A next episode added this recently is « nouveau ». */
    val NEW_EPISODE: Duration = Duration.ofDays(14)

    /** Watched longer ago than this, a title is worth rediscovering. */
    val LONG_AGO: Duration = Duration.ofDays(365)

    /**
     * The tiles, the large one first: the latest playback in progress, else the latest episode to
     * watch next, else the latest addition. Then, in this order: the other playbacks in progress,
     * the episodes to watch next, the new episodes of series followed, the latest additions, a title
     * to rediscover.
     */
    fun pick(
        sources: TonightSources,
        now: Instant,
    ): List<TonightTile> {
        val (fresh, next) = sources.nextUp.partition { it.isNew(now) }
        val resume = sources.resume.map { TonightTile(it, TonightKind.Resume) }
        val nextUp = next.map { TonightTile(it, TonightKind.NextUp) }
        val new = fresh.map { TonightTile(it, TonightKind.NewEpisode) }
        val added = sources.latest.map { TonightTile(it, TonightKind.Added) }
        val rediscover = rediscover(sources.rediscover, now).map { TonightTile(it, TonightKind.Rediscover) }
        // The server lists the next episodes the series watched last first.
        val latestNext = sources.nextUp.firstOrNull()?.let { first -> (nextUp + new).first { it.item == first } }
        val large = resume.firstOrNull() ?: latestNext ?: added.firstOrNull() ?: rediscover.firstOrNull()
        val tiles = mutableListOf<TonightTile>()
        val seen = mutableSetOf<ItemId>()

        fun add(tile: TonightTile) {
            if (tiles.size >= TILES) return
            val key = tile.item.seriesOrSelf
            if (seen.add(key)) tiles += tile
        }
        large?.let(::add)
        (resume + nextUp + new + added + rediscover).forEach(::add)
        return tiles
    }

    /** Never watched first (the oldest additions), then the titles watched longest ago, one a day. */
    private fun rediscover(
        items: List<MediaItem>,
        now: Instant,
    ): List<MediaItem> {
        val never = items.filter { it.userData?.played != true }
        val longAgo =
            items
                .filter { it.userData?.played == true }
                .filter { (it.userData?.lastPlayed ?: Instant.EPOCH) < now.minus(LONG_AGO) }
                .sortedBy { it.userData?.lastPlayed ?: Instant.EPOCH }
        val candidates = never + longAgo
        if (candidates.isEmpty()) return emptyList()
        // Another title each day, the same all day long: the mosaic does not change at each return.
        val day = (now.epochSecond / SECONDS_PER_DAY).toInt()
        val start = Math.floorMod(day, candidates.size)
        return candidates.drop(start) + candidates.take(start)
    }

    private fun MediaItem.isNew(now: Instant): Boolean =
        kind == ItemKind.Episode && dateAdded?.let { Duration.between(it, now) <= NEW_EPISODE } == true

    private val MediaItem.seriesOrSelf: ItemId get() = episode?.seriesId ?: id

    private const val SECONDS_PER_DAY = 86_400L
}
