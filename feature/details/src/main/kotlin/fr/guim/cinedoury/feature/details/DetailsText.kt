package fr.guim.cinedoury.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.SeriesStatus
import fr.guim.cinedoury.core.model.SmartPlay
import fr.guim.cinedoury.core.model.endsAt
import fr.guim.cinedoury.core.model.remaining
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.core.ui.SEPARATOR
import fr.guim.cinedoury.core.ui.metadataLine
import fr.guim.cinedoury.core.ui.toDateText
import fr.guim.cinedoury.core.ui.toDurationText
import fr.guim.cinedoury.core.ui.toTimeText
import java.time.ZonedDateTime

/** « S02E05 »: an episode in a button. */
@Composable
internal fun MediaItem.shortCode(): String? {
    val season = episode?.seasonNumber ?: return null
    val number = episode?.episodeNumber ?: return null
    return stringResource(R.string.episode_short, season, number)
}

/**
 * The line of metadata of a details screen (`UI.md` § 8): « 2014 · 2 h 49 · 12 ans · ★ 7,4 · se
 * termine à 23 h 36 » for a film, « 2015–2016 · Terminée · 2 saisons » for a series.
 */
@Composable
internal fun MediaItem.detailsLine(): String? =
    when (kind) {
        ItemKind.Series -> {
            seriesLine()
        }

        ItemKind.Person -> {
            personLine()
        }

        else -> {
            val parts = listOfNotNull(metadataLine(), endLine())
            parts.joinToString(SEPARATOR).ifEmpty { null }
        }
    }

/** « se termine à 22 h 47 », from now: it follows the clock while the screen is open. */
@Composable
internal fun MediaItem.endLine(): String? {
    if (kind !in setOf(ItemKind.Movie, ItemKind.Episode, ItemKind.Video)) return null
    val end = endsAt(ZonedDateTime.now(LocalClock.current)) ?: return null
    return stringResource(R.string.ends_at, end.toTimeText())
}

@Composable
private fun MediaItem.seriesLine(): String? {
    val first = premiereDate?.year ?: year
    val last = endDate?.year
    val years =
        when {
            first == null -> null
            seriesStatus == SeriesStatus.Continuing -> stringResource(R.string.years_since, first)
            last != null && last != first -> stringResource(R.string.years_range, first, last)
            else -> first.toString()
        }
    val status =
        when (seriesStatus) {
            SeriesStatus.Continuing -> stringResource(R.string.status_continuing)
            SeriesStatus.Ended -> stringResource(R.string.status_ended)
            null -> null
        }
    val seasons = childCount?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.season_count, it, it) }
    return listOfNotNull(years, status, seasons, officialRating).joinToString(SEPARATOR).ifEmpty { null }
}

/** « Naissance : 2 mars 1970, Paris ». */
@Composable
private fun MediaItem.personLine(): String? {
    val birth = premiereDate?.toDateText()
    val place = productionLocations.firstOrNull()
    val born = listOfNotNull(birth, place).joinToString(", ").ifEmpty { null }
    val parts =
        listOfNotNull(
            born?.let { stringResource(R.string.person_born, it) },
            endDate?.toDateText()?.let { stringResource(R.string.person_died, it) },
        )
    return parts.joinToString(SEPARATOR).ifEmpty { null }
}

/** « Réalisation : Marie Duval · Scénario : Claire Fontaine ». */
@Composable
internal fun MediaItem.crewLine(): String? {
    fun names(
        type: CreditType,
    ) = people
        .filter { it.type == type }
        .map { it.name }
        .distinct()
        .joinToString(", ")
        .ifEmpty { null }
    val parts =
        listOfNotNull(
            names(CreditType.Director)?.let { stringResource(R.string.credits_directed, it) },
            names(CreditType.Writer)?.let { stringResource(R.string.credits_written, it) },
            names(CreditType.Creator)?.let { stringResource(R.string.credits_created, it) },
        )
    return parts.joinToString(SEPARATOR).ifEmpty { null }
}

/**
 * The main button (`FONCTIONNALITES.md` § 4): « Reprendre — 1 h 09 restantes » or « Lire » for a
 * film or an episode; for a series, « Reprendre S02E05 — 12 min restantes » or « Lire S02E05 ».
 */
@Composable
internal fun DetailsState.playLabel(): String? {
    val play = smartPlay
    return when {
        item.kind == ItemKind.Series && play != null -> {
            play.label()
        }

        item.kind == ItemKind.Series -> {
            null
        }

        !playable -> {
            null
        }

        item.progress != null -> {
            item.remaining?.let {
                stringResource(
                    R.string.action_resume_remaining,
                    it.toDurationText(),
                )
            }
        }

        else -> {
            stringResource(R.string.action_play)
        }
    }
}

@Composable
private fun SmartPlay.label(): String {
    val code = episode.shortCode() ?: episode.title
    val left = remaining
    return if (resume && left != null) {
        stringResource(R.string.action_resume_episode, code, left.toDurationText())
    } else {
        stringResource(R.string.action_play_episode, code)
    }
}

/** « S01 · E02 · 42 min · 9 mars 2015 »: the line of an episode in the list. */
@Composable
internal fun MediaItem.episodeLine(): String? {
    val parts =
        listOfNotNull(
            runtime?.takeIf { it.isPositive() }?.toDurationText(),
            premiereDate?.toDateText(),
        )
    return parts.joinToString(SEPARATOR).ifEmpty { null }
}

/** « 2. Mortes-eaux »: the number and the title of an episode. */
@Composable
internal fun MediaItem.numberedTitle(): String =
    episode?.episodeNumber?.let { stringResource(R.string.episode_numbered, it, title) } ?: title
