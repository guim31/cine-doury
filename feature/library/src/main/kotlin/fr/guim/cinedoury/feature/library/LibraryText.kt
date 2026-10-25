package fr.guim.cinedoury.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Resolution
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import fr.guim.cinedoury.core.ui.SEPARATOR
import java.util.Locale

@Composable
internal fun LibraryTab.label(): String =
    stringResource(
        when (this) {
            LibraryTab.All -> R.string.tab_all
            LibraryTab.Collections -> R.string.tab_collections
            LibraryTab.Genres -> R.string.tab_genres
            LibraryTab.NextUp -> R.string.tab_next_up
        },
    )

@Composable
internal fun SortField.label(): String =
    stringResource(
        when (this) {
            SortField.Title -> R.string.sort_title
            SortField.DateAdded -> R.string.sort_date_added
            SortField.ReleaseDate -> R.string.sort_release
            SortField.Rating -> R.string.sort_rating
            SortField.Runtime -> R.string.sort_runtime
            SortField.LastPlayed -> R.string.sort_last_played
            SortField.PlayCount -> R.string.sort_play_count
            SortField.Random -> R.string.sort_random
        },
    )

/** « A → Z » for titles, « Croissant » or « Décroissant » otherwise; nothing for a random order. */
@Composable
internal fun ItemSort.directionLabel(): String? =
    when {
        field == SortField.Random -> null
        field == SortField.Title -> stringResource(if (ascending) R.string.sort_a_z else R.string.sort_z_a)
        else -> stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending)
    }

@Composable
internal fun LibraryView.label(): String =
    stringResource(
        when (this) {
            LibraryView.SmallPosters -> R.string.view_small
            LibraryView.Posters -> R.string.view_posters
            LibraryView.LargePosters -> R.string.view_large
            LibraryView.List -> R.string.view_list
            LibraryView.Thumbnails -> R.string.view_thumbnails
        },
    )

@Composable
internal fun WatchStatus.label(): String =
    stringResource(
        when (this) {
            WatchStatus.Unwatched -> R.string.filter_unwatched
            WatchStatus.Watched -> R.string.filter_watched
            WatchStatus.InProgress -> R.string.filter_in_progress
        },
    )

internal val Resolution.label: String
    get() =
        when (this) {
            Resolution.Sd -> "SD"
            Resolution.Hd -> "720p"
            Resolution.FullHd -> "1080p"
            Resolution.Uhd -> "4K"
        }

/** « années 80 », « années 2000 » (« 1980s » in English). */
@Composable
internal fun decadeLabel(decade: Int): String {
    val short = booleanResource(R.bool.decade_short) && decade < SHORT_DECADES_BEFORE
    return stringResource(R.string.decade, if (short) (decade % CENTURY).toString() else decade.toString())
}

private const val SHORT_DECADES_BEFORE = 2000
private const val CENTURY = 100

/**
 * The name of a language from its ISO 639-2 code (« fra » → « français »), in the language of the
 * device; the code itself when unknown.
 */
internal fun languageName(code: String): String {
    val twoLetters = BIBLIOGRAPHIC[code] ?: ISO3[code] ?: code
    val name = Locale.forLanguageTag(twoLetters).getDisplayLanguage(Locale.getDefault())
    return name.ifEmpty { code }.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

private val ISO3: Map<String, String> by lazy {
    Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).isO3Language }
}

/** ISO 639-2/B codes some files carry instead of the 639-2/T ones Java knows. */
private val BIBLIOGRAPHIC =
    mapOf("fre" to "fr", "ger" to "de", "chi" to "zh", "dut" to "nl", "gre" to "el", "cze" to "cs", "rum" to "ro")

/** « Comédie · années 80 · non vus »: the filters in use, in a line. */
@Composable
internal fun ItemFilters.summary(): String {
    val parts = mutableListOf<String>()
    watch?.let {
        parts +=
            stringResource(
                when (it) {
                    WatchStatus.Unwatched -> R.string.summary_unwatched
                    WatchStatus.Watched -> R.string.summary_watched
                    WatchStatus.InProgress -> R.string.summary_in_progress
                },
            )
    }
    parts += genres.sorted()
    parts += decades.sorted().map { decadeLabel(it) }
    parts += years.sorted().map { it.toString() }
    minRating?.let { parts += stringResource(R.string.summary_rating, it) }
    resolution?.let { parts += it.label }
    parts += audioLanguages.sorted().map(::languageName)
    subtitleLanguages.sorted().forEach {
        parts +=
            stringResource(R.string.summary_subtitles, languageName(it).lowercase())
    }
    parts += officialRatings.sorted()
    parts += studios.map { it.name }.sorted()
    parts += people.map { it.name }.sorted()
    collection?.let { parts += it.name }
    parts += tags.sorted()
    if (favorite) parts += stringResource(R.string.summary_favorites)
    // Only the first word of the line takes a capital (`UI.md` § 5).
    val locale = LocalConfiguration.current.locales[0]
    return parts.joinToString(SEPARATOR).replaceFirstChar { it.titlecase(locale) }
}
