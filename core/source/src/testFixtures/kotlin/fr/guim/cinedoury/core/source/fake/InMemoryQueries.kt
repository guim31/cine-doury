package fr.guim.cinedoury.core.source.fake

import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate

/**
 * The queries of [InMemorySource], close to the server's: the sort title is lowercased and without
 * accents or punctuation, as Jellyfin writes it. Filters on what a [fr.guim.cinedoury.core.model.MediaItem]
 * does not carry (studios, people, tags, collection) let every item through.
 */
internal object InMemoryQueries {
    fun sortKey(item: MediaItem): String =
        Normalizer
            .normalize((item.sortTitle ?: item.title).lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .filter { it.isLetterOrDigit() || it == ' ' }

    @Suppress("CyclomaticComplexMethod") // One condition per filter.
    fun matches(
        item: MediaItem,
        filters: ItemFilters,
    ): Boolean {
        val data = item.userData
        val watch =
            when (filters.watch) {
                null -> true
                WatchStatus.Watched -> data?.played == true
                WatchStatus.Unwatched -> data?.played != true
                WatchStatus.InProgress -> data?.played != true && data?.playbackPosition?.isPositive() == true
            }
        val years = filters.allYears
        val video = item.mediaSources.firstOrNull()?.video
        val resolution =
            filters.resolution?.let { wanted ->
                val width = video?.width ?: return false
                (wanted.minWidth == null || width >= wanted.minWidth!!) &&
                    (wanted.maxWidth == null || width <= wanted.maxWidth!!)
            } ?: true
        val audio = item.mediaSources.flatMap { source -> source.audio.mapNotNull { it.language } }
        val subtitles = item.mediaSources.flatMap { source -> source.subtitles.mapNotNull { it.language } }
        return watch &&
            resolution &&
            (filters.genres.isEmpty() || item.genres.any { it in filters.genres }) &&
            (years.isEmpty() || item.year in years) &&
            (filters.minRating == null || (item.communityRating ?: 0f) >= filters.minRating!!) &&
            (filters.audioLanguages.isEmpty() || audio.any { it in filters.audioLanguages }) &&
            (filters.subtitleLanguages.isEmpty() || subtitles.any { it in filters.subtitleLanguages }) &&
            (filters.officialRatings.isEmpty() || item.officialRating in filters.officialRatings) &&
            (!filters.favorite || data?.isFavorite == true)
    }

    fun sort(
        items: List<MediaItem>,
        sort: ItemSort,
    ): List<MediaItem> {
        val byTitle = compareBy<MediaItem> { sortKey(it) }
        val comparator: Comparator<MediaItem> =
            when (sort.field) {
                SortField.Title -> byTitle

                SortField.DateAdded -> compareBy { it.dateAdded ?: Instant.EPOCH }

                SortField.ReleaseDate -> compareBy { it.premiereDate ?: LocalDate.MIN }

                SortField.Rating -> compareBy { it.communityRating ?: 0f }

                SortField.Runtime -> compareBy { it.runtime }

                SortField.LastPlayed -> compareBy { it.userData?.lastPlayed ?: Instant.EPOCH }

                SortField.PlayCount -> compareBy { it.userData?.playCount ?: 0 }

                // Stable for tests: the fake has no randomness.
                SortField.Random -> compareBy { it.id.value.hashCode() }
            }
        val directed = if (sort.ascending) comparator else comparator.reversed()
        return items.sortedWith(directed.thenComparing(byTitle))
    }
}
