package fr.guim.cinedoury.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import java.text.NumberFormat
import kotlin.time.Duration

/** « 1 h 52 », « 42 min » (`UI.md` § 5), with non-breaking spaces. */
@Composable
fun Duration.toDurationText(): String {
    val minutes = inWholeMinutes
    return if (minutes >= MINUTES_PER_HOUR) {
        stringResource(R.string.duration_hours, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    } else {
        stringResource(R.string.duration_minutes, minutes.coerceAtLeast(1))
    }
}

private const val MINUTES_PER_HOUR = 60L

/** The title a card shows: the series of an episode, the item otherwise. */
val MediaItem.cardTitle: String
    get() = if (kind == ItemKind.Episode) episode?.seriesTitle ?: title else title

/** « S02 · E05 », or null when the numbers are unknown. */
@Composable
fun MediaItem.episodeCode(): String? {
    val season = episode?.seasonNumber ?: return null
    val number = episode?.episodeNumber ?: return null
    return stringResource(R.string.episode_code, season, number)
}

/**
 * The line under a card: « S02 · E05 — titre » for an episode, « Reprendre à 42 min » for a film
 * started, the year otherwise.
 */
@Composable
fun MediaItem.cardSubtitle(): String? {
    val code = if (kind == ItemKind.Episode) episodeCode() else null
    val position = userData?.playbackPosition?.takeIf { progress != null }
    return when {
        kind == ItemKind.Episode -> code?.let { stringResource(R.string.episode_line, it, title) } ?: title
        position != null -> stringResource(R.string.resume_at, position.toDurationText())
        else -> year?.toString()
    }
}

/** « 2014 · 2 h 49 · -12 · ★ 7,4 »: what is known of the item, in that order. */
@Composable
fun MediaItem.metadataLine(): String? {
    val parts =
        listOfNotNull(
            if (kind == ItemKind.Episode) episodeCode() else year?.toString(),
            runtime?.takeIf { it.isPositive() }?.toDurationText(),
            officialRating,
            communityRating?.let { stringResource(R.string.rating, ratingFormat().format(it)) },
        )
    return parts.joinToString(SEPARATOR).ifEmpty { null }
}

@Composable
private fun ratingFormat(): NumberFormat =
    NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }

/** Between the parts of a line of metadata. */
const val SEPARATOR = " · "

/** The line that sells the item in the banner: its tagline, else the first sentence of its synopsis. */
val MediaItem.catchLine: String?
    get() =
        tagline ?: overview?.let { text ->
            val end = text.indexOfAny(charArrayOf('.', '!', '?'))
            if (end in 1 until text.length) text.substring(0, end + 1) else text
        }
