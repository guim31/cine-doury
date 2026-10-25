package fr.guim.cinedoury.core.model

import java.time.LocalDate

/** What a list of items is sorted by (`FONCTIONNALITES.md` § 3). */
enum class SortField {
    Title,
    DateAdded,
    ReleaseDate,
    Rating,
    Runtime,
    LastPlayed,
    PlayCount,
    Random,
    ;

    /** The usual direction: A to Z for titles, the newest, best or longest first otherwise. */
    val defaultAscending: Boolean get() = this == Title
}

data class ItemSort(
    val field: SortField = SortField.Title,
    val ascending: Boolean = field.defaultAscending,
)

/** Whether the user has seen an item. */
enum class WatchStatus {
    Unwatched,
    Watched,
    InProgress,
}

/** Video resolution of a film, by the width of its picture (a 2.39:1 film in 4K is 3840 × 1608). */
enum class Resolution(
    val minWidth: Int?,
    val maxWidth: Int?,
) {
    Sd(minWidth = null, maxWidth = 1199),
    Hd(minWidth = 1200, maxWidth = 1899),
    FullHd(minWidth = 1900, maxWidth = 3799),
    Uhd(minWidth = 3800, maxWidth = null),
    ;

    companion object {
        fun of(width: Int): Resolution =
            entries.first {
                (it.minWidth == null || width >= it.minWidth) &&
                    (it.maxWidth == null || width <= it.maxWidth)
            }
    }
}

/** A value of a filter known to the server by an id: a studio, a person, a collection. */
data class NamedId(
    val id: String,
    val name: String,
)

/**
 * The filters of a library (`FONCTIONNALITES.md` § 3), all applied by the server. Values of one
 * filter add up (Comédie or Drame); filters combine (Comédie and non vus).
 *
 * @param decades first year of each decade (1980 for the 1980s).
 * @param minRating minimal community rating, out of 10.
 * @param audioLanguages ISO 639-2 codes (« fra », « eng »).
 */
data class ItemFilters(
    val watch: WatchStatus? = null,
    val genres: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val years: Set<Int> = emptySet(),
    val minRating: Int? = null,
    val resolution: Resolution? = null,
    val audioLanguages: Set<String> = emptySet(),
    val subtitleLanguages: Set<String> = emptySet(),
    val officialRatings: Set<String> = emptySet(),
    val studios: Set<NamedId> = emptySet(),
    val people: Set<NamedId> = emptySet(),
    val collection: NamedId? = null,
    val tags: Set<String> = emptySet(),
    val favorite: Boolean = false,
    val ranges: Set<DynamicRange> = emptySet(),
    val audioFormats: Set<AudioFormat> = emptySet(),
    val videoCodecs: Set<VideoCodec> = emptySet(),
) {
    /** Number of filters in use, for the summary. */
    val count: Int
        get() =
            listOf(
                watch != null,
                genres.isNotEmpty(),
                decades.isNotEmpty() || years.isNotEmpty(),
                minRating != null,
                resolution != null,
                audioLanguages.isNotEmpty(),
                subtitleLanguages.isNotEmpty(),
                officialRatings.isNotEmpty(),
                studios.isNotEmpty(),
                people.isNotEmpty(),
                collection != null,
                tags.isNotEmpty(),
                favorite,
                ranges.isNotEmpty(),
                audioFormats.isNotEmpty(),
                videoCodecs.isNotEmpty(),
            ).count { it }

    /**
     * Whether some filters are applied by the local index: Jellyfin 12.3 cannot filter on the dynamic
     * range, the audio format or the codec (`SPEC.md` § 4.10).
     */
    val usesIndex: Boolean get() = ranges.isNotEmpty() || audioFormats.isNotEmpty() || videoCodecs.isNotEmpty()

    /** The filters the server applies: these minus the ones of the local index. */
    val forServer: ItemFilters get() = copy(ranges = emptySet(), audioFormats = emptySet(), videoCodecs = emptySet())

    val isEmpty: Boolean get() = this == NONE

    /** The years asked for, decades expanded; empty when no year filter is set. */
    val allYears: Set<Int> get() = years + decades.flatMap { it until it + DECADE }

    companion object {
        val NONE = ItemFilters()
        const val DECADE = 10
    }
}

/**
 * A query over the items of a library, run by the server and read page by page.
 *
 * @param library where to look; null for every library.
 * @param kinds the kinds of items to return; empty for the top-level items of the library.
 * @param groupCollections films of a collection are shown as their collection.
 * @param titleBelow only the items whose sort title comes before this one (for the A–Z jump: their
 * count is the index of the first item at that letter).
 * @param releasedBefore only items released on that day or before.
 * @param ids only these items (at most [MAX_IDS], the length of a URL).
 */
data class ItemQuery(
    val library: LibraryId? = null,
    val kinds: Set<ItemKind> = emptySet(),
    val sort: ItemSort = ItemSort(),
    val filters: ItemFilters = ItemFilters.NONE,
    val groupCollections: Boolean = false,
    val titleBelow: String? = null,
    val releasedBefore: LocalDate? = null,
    /** Only these items, whatever the library; the order of the answer is the server's. */
    val ids: List<ItemId> = emptyList(),
)

/** The most items an [ItemQuery] can name by id: the server refuses longer addresses. */
const val MAX_IDS = 100

/** The values the filters of a library can take, as the server lists them. */
data class FilterOptions(
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val officialRatings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val audioLanguages: List<String> = emptyList(),
    val subtitleLanguages: List<String> = emptyList(),
    val studios: List<NamedId> = emptyList(),
    val people: List<NamedId> = emptyList(),
    val collections: List<NamedId> = emptyList(),
) {
    /** The decades of [years], oldest first. */
    val decades: List<Int> get() = years.map { it - it % ItemFilters.DECADE }.distinct().sorted()
}

/**
 * The library settings of the user on the server (Jellyfin: `OrderedViews`, `MyMediaExcludes`,
 * `LatestItemsExcludes`): libraries left out of the home, and out of « Ajouts récents ».
 */
data class HomeExclusions(
    val fromHome: Set<LibraryId> = emptySet(),
    val fromLatest: Set<LibraryId> = emptySet(),
)
