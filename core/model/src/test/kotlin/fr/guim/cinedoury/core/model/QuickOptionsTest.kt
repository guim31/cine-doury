package fr.guim.cinedoury.core.model

import fr.guim.cinedoury.core.model.QuickOption.AddFavorite
import fr.guim.cinedoury.core.model.QuickOption.GoToSeason
import fr.guim.cinedoury.core.model.QuickOption.GoToSeries
import fr.guim.cinedoury.core.model.QuickOption.MarkUnwatched
import fr.guim.cinedoury.core.model.QuickOption.MarkWatched
import fr.guim.cinedoury.core.model.QuickOption.OpenDetails
import fr.guim.cinedoury.core.model.QuickOption.Play
import fr.guim.cinedoury.core.model.QuickOption.PlayFromStart
import fr.guim.cinedoury.core.model.QuickOption.RemoveFavorite
import fr.guim.cinedoury.core.model.QuickOption.RemoveFromContinue
import fr.guim.cinedoury.core.model.QuickOption.Resume
import fr.guim.cinedoury.core.model.QuickOption.Select
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class QuickOptionsTest {
    private fun data(
        played: Boolean = false,
        position: Duration = Duration.ZERO,
        favorite: Boolean = false,
    ) = UserData(played, position, 0, favorite)

    private val film = MediaItem(ItemId("film"), ItemKind.Movie, "Le Phare", runtime = 100.minutes, userData = data())

    private val episode =
        MediaItem(
            ItemId("ep"),
            ItemKind.Episode,
            "Mortes-eaux",
            runtime = 42.minutes,
            userData = data(position = 18.minutes),
            episode = EpisodeInfo(ItemId("series"), "Les Marées", ItemId("season"), 1, 2),
        )

    @Test
    fun `a film never started plays, a film watched can be marked unwatched`() {
        assertEquals(listOf(Play, OpenDetails, MarkWatched, AddFavorite), QuickOptions.of(film))
        val watched = film.copy(userData = data(played = true, favorite = true))
        assertEquals(listOf(Play, OpenDetails, MarkUnwatched, RemoveFavorite), QuickOptions.of(watched))
    }

    @Test
    fun `an episode started resumes, plays from the start and leads to its series and season`() {
        assertEquals(
            listOf(Resume, PlayFromStart, OpenDetails, GoToSeries, GoToSeason, MarkWatched, AddFavorite),
            QuickOptions.of(episode),
        )
    }

    @Test
    fun `on the home, a playback in progress can leave Continuer`() {
        assertEquals(
            listOf(
                Resume,
                PlayFromStart,
                OpenDetails,
                GoToSeries,
                GoToSeason,
                MarkWatched,
                RemoveFromContinue,
                AddFavorite,
            ),
            QuickOptions.of(episode, inContinue = true),
        )
        // Not started: nothing to remove.
        assertEquals(listOf(Play, OpenDetails, MarkWatched, AddFavorite), QuickOptions.of(film, inContinue = true))
    }

    @Test
    fun `a series opens its details, and is marked watched as a whole`() {
        val series = MediaItem(ItemId("series"), ItemKind.Series, "Les Marées", userData = data())
        assertEquals(listOf(OpenDetails, MarkWatched, AddFavorite), QuickOptions.of(series))
        val season = MediaItem(ItemId("s"), ItemKind.Season, "Saison 1", userData = data(played = true))
        assertEquals(listOf(MarkUnwatched, AddFavorite), QuickOptions.of(season))
        val person = MediaItem(ItemId("p"), ItemKind.Person, "Inès Garnier")
        assertEquals(listOf(OpenDetails, AddFavorite), QuickOptions.of(person))
    }

    @Test
    fun `Selectionner comes last, for the profiles that manage a library`() {
        assertEquals(
            listOf(Play, OpenDetails, MarkWatched, AddFavorite, Select),
            QuickOptions.of(film, canSelect = true),
        )
    }

    @Test
    fun `the expected data after an option`() {
        val started = data(position = 18.minutes)
        assertEquals(data(played = true), QuickOptions.expected(MarkWatched, started))
        assertEquals(data(), QuickOptions.expected(RemoveFromContinue, started))
        assertEquals(started.copy(isFavorite = true), QuickOptions.expected(AddFavorite, started))
    }
}
