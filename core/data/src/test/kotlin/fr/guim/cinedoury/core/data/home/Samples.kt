package fr.guim.cinedoury.core.data.home

import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.UserData
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Libraries and items for the tests of the home. */
internal object Samples {
    val films = Library(LibraryId("films"), "Films", LibraryKind.Movies)
    val series = Library(LibraryId("series"), "Séries", LibraryKind.Shows)
    val shows = Library(LibraryId("spectacles"), "Spectacles", LibraryKind.Movies)
    val collections = Library(LibraryId("collections"), "Collections", LibraryKind.Collections)
    val all = listOf(series, films, shows, collections)

    fun film(
        id: String,
        year: Int = 2000,
        position: Duration = Duration.ZERO,
        lastPlayed: Instant? = null,
        added: Instant? = null,
        played: Boolean = false,
    ) = MediaItem(
        id = ItemId(id),
        kind = ItemKind.Movie,
        title = id,
        year = year,
        premiereDate = LocalDate.of(year, 6, 15),
        dateAdded = added,
        runtime = 100.minutes,
        userData = UserData(played, position, if (played) 1 else 0, isFavorite = false, lastPlayed = lastPlayed),
    )
}
