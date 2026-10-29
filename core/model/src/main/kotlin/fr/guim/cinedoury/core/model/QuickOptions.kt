package fr.guim.cinedoury.core.model

/** A line of the menu of a card, opened by OK held (essai réel 5, `SPEC.md` § 4.23). */
enum class QuickOption {
    Resume,
    Play,
    PlayFromStart,
    OpenDetails,
    GoToSeries,
    GoToSeason,
    MarkWatched,
    MarkUnwatched,
    RemoveFromContinue,
    AddFavorite,
    RemoveFavorite,

    /** Starts a selection in a library, for the profiles that manage (J6). */
    Select,
}

/**
 * The menu of a card, chosen by the maintainer (essai réel 5): play, « vu », favourites; never a
 * management action (identification, images, deletion stay behind « ⋯ » and the management bar).
 */
object QuickOptions {
    /** What plays at once: a film, an episode, a video. A series or a season opens its details. */
    private val PLAYABLE = setOf(ItemKind.Movie, ItemKind.Episode, ItemKind.Video)

    /** What the server marks watched: for a series or a season, every episode. */
    private val WATCHABLE = setOf(ItemKind.Movie, ItemKind.Episode, ItemKind.Video, ItemKind.Series, ItemKind.Season)

    /**
     * The lines for [item], in order: play, details and the series, « vu », favourites, then
     * « Sélectionner ».
     *
     * @param inContinue the card is a playback in progress on the home: « Retirer de Continuer ».
     * @param canSelect the card is in a library and the profile manages it.
     */
    fun of(
        item: MediaItem,
        inContinue: Boolean = false,
        canSelect: Boolean = false,
    ): List<QuickOption> =
        buildList {
            addAll(play(item))
            // A season is a tab of the details of its series: it opens nothing more.
            if (item.kind != ItemKind.Season) add(QuickOption.OpenDetails)
            addAll(series(item))
            if (item.kind in WATCHABLE) {
                add(if (item.userData?.played == true) QuickOption.MarkUnwatched else QuickOption.MarkWatched)
            }
            if (inContinue && item.progress != null) add(QuickOption.RemoveFromContinue)
            if (item.kind != ItemKind.Folder && item.kind != ItemKind.Other) {
                add(if (item.userData?.isFavorite == true) QuickOption.RemoveFavorite else QuickOption.AddFavorite)
            }
            if (canSelect) add(QuickOption.Select)
        }

    /** « Reprendre » and « Lire depuis le début » once started, else « Lire ». */
    private fun play(item: MediaItem): List<QuickOption> =
        when {
            item.kind !in PLAYABLE -> emptyList()
            item.progress != null -> listOf(QuickOption.Resume, QuickOption.PlayFromStart)
            else -> listOf(QuickOption.Play)
        }

    /** The series and the season of an episode. */
    private fun series(item: MediaItem): List<QuickOption> {
        val episode = item.episode?.takeIf { item.kind == ItemKind.Episode } ?: return emptyList()
        if (episode.seriesId == null) return emptyList()
        return listOfNotNull(QuickOption.GoToSeries, QuickOption.GoToSeason.takeIf { episode.seasonId != null })
    }

    /** [data] after [option], as the server will answer, for the cards at once. */
    fun expected(
        option: QuickOption,
        data: UserData,
    ): UserData =
        when (option) {
            QuickOption.MarkWatched -> data.copy(played = true, playbackPosition = kotlin.time.Duration.ZERO)
            QuickOption.MarkUnwatched -> data.copy(played = false, playbackPosition = kotlin.time.Duration.ZERO)
            QuickOption.RemoveFromContinue -> data.copy(playbackPosition = kotlin.time.Duration.ZERO)
            QuickOption.AddFavorite -> data.copy(isFavorite = true)
            QuickOption.RemoveFavorite -> data.copy(isFavorite = false)
            else -> data
        }
}
