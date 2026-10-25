package fr.guim.cinedoury.core.data.search

import androidx.room.Room
import fr.guim.cinedoury.core.data.db.CineDatabase
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.UserData
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/** The title index and the search on an in-memory Room, filled from an in-memory source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchRepositoryTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CineDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** A clock moved by hand. */
    private class TestClock(
        var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = TestClock(Instant.parse("2026-10-25T20:00:00Z"))
    private val titleIndex = TitleIndex(database.search(), clock, pageSize = 2)
    private val search = SearchRepository(database.search(), clock)
    private val profile = ProfileKey(SERVER, "u1")

    private val films = Library(LibraryId("films"), "Films", LibraryKind.Movies)
    private val series = Library(LibraryId("series"), "Séries", LibraryKind.Shows)
    private val kids = Library(LibraryId("kids"), "Enfants", LibraryKind.Movies)

    private fun film(
        id: String,
        title: String,
        year: Int? = null,
        original: String? = null,
    ) = MediaItem(
        ItemId(id),
        ItemKind.Movie,
        title,
        sortTitle = title.lowercase(),
        originalTitle = original,
        year = year,
        dateAdded = Instant.parse("2026-01-01T00:00:00Z"),
        userData = UserData(played = false, playbackPosition = Duration.ZERO, playCount = 0, isFavorite = false),
    )

    private val amelie = film("amelie", "Le Fabuleux Destin d'Amélie Poulain", 2001, original = "Amélie")
    private val seigneur = film("lotr", "Le Seigneur des anneaux", 2001)
    private val eclipse = film("eclipse", "L'Éclipse", 1972)
    private val zephyr = film("zephyr", "Zéphyr", 2022)
    private val marees = MediaItem(ItemId("marees"), ItemKind.Series, "Les Marées", year = 2015)
    private val grandeMaree =
        MediaItem(
            ItemId("marees-1"),
            ItemKind.Episode,
            "Grande marée",
            episode = EpisodeInfo(ItemId("marees"), "Les Marées", ItemId("s1"), 1, 1),
        )
    private val camille = MediaItem(ItemId("camille"), ItemKind.Person, "Camille Martin")
    private val saga = MediaItem(ItemId("saga"), ItemKind.Collection, "Les Anneaux : la saga")

    private suspend fun source(
        filmsHeld: List<MediaItem> = listOf(amelie, seigneur, eclipse),
        kidsHeld: List<MediaItem> = listOf(zephyr),
    ): LibrarySource {
        val fake =
            InMemorySource(
                libraries = listOf(films, series, kids),
                contents = mapOf(films.id to filmsHeld, series.id to listOf(marees), kids.id to kidsHeld),
                extras = listOf(grandeMaree, camille, saga),
            )
        return fake.open(fake.signIn(ADDRESS, Credentials.Password("admin", "password")))
    }

    @AfterTest
    fun close() = database.close()

    private suspend fun indexed() = search.titles(SERVER).first()

    private val everywhere = setOf(films.id, series.id, kids.id)

    @Test
    fun `the title index holds the films and series, page by page, and follows deletions`() =
        runTest {
            titleIndex.sync(SERVER, source(), listOf(films, series, kids))
            assertEquals(
                setOf("amelie", "lotr", "eclipse", "zephyr", "marees"),
                indexed().map { it.item.id.value }.toSet(),
            )
            val amelieCard = indexed().first { it.item.id == amelie.id }.item
            assertEquals("Amélie", amelieCard.originalTitle)
            assertEquals(2001, amelieCard.year)

            // A film deleted on the server: the counts differ, the library is read again.
            clock.now = clock.now.plusSeconds(3_600)
            titleIndex.sync(SERVER, source(filmsHeld = listOf(amelie, seigneur)), listOf(films, series, kids))
            assertTrue(indexed().none { it.item.id == eclipse.id })
            // A library the profile does not see any more.
            titleIndex.sync(SERVER, source(), listOf(films, series))
            assertTrue(indexed().none { it.item.id == zephyr.id })
        }

    @Test
    fun `local - accents, typos and original titles, best first, within the libraries of the profile`() =
        runTest {
            titleIndex.sync(SERVER, source(), listOf(films, series, kids))
            val titles = indexed()
            assertEquals(listOf("amelie"), search.local("amelie", titles, everywhere).map { it.item.id.value })
            assertEquals(listOf("lotr"), search.local("seigneur anaux", titles, everywhere).map { it.item.id.value })
            assertEquals(listOf("eclipse"), search.local("eclipce", titles, everywhere).map { it.item.id.value })
            assertEquals(
                "marees",
                search
                    .local("marées", titles, everywhere)
                    .first()
                    .item.id.value,
            )
            // From the first letter, the start of a title first.
            assertEquals(
                "zephyr",
                search
                    .local("z", titles, everywhere)
                    .first()
                    .item.id.value,
            )
            // A library the profile cannot see.
            assertEquals(emptyList(), search.local("zephyr", titles, setOf(films.id, series.id)))
            assertEquals(emptyList(), search.local("   ", titles, everywhere))
        }

    @Test
    fun `complete - the server confirms the titles and adds people, collections and episodes`() =
        runTest {
            titleIndex.sync(SERVER, source(), listOf(films, series, kids))
            val local = search.local("mare", indexed(), everywhere)
            val all = search.complete(source(), "mare", local)
            val ids = all.map { it.item.id.value }
            assertTrue("marees" in ids && "marees-1" in ids, ids.toString())
            // The cards of the server, with what the profile watched.
            assertTrue(all.first { it.item.id == marees.id }.score > 1)

            val people = search.complete(source(), "camille", search.local("camille", indexed(), everywhere))
            assertEquals(listOf("camille"), people.map { it.item.id.value })
            val collections = search.complete(source(), "anneaux", search.local("anneaux", indexed(), everywhere))
            assertEquals(setOf("lotr", "saga"), collections.map { it.item.id.value }.toSet())
            val confirmed = collections.first { it.item.id == seigneur.id }.item
            assertTrue(confirmed.userData != null)
        }

    @Test
    fun `complete - a title the server does not give back any more is dropped`() =
        runTest {
            titleIndex.sync(SERVER, source(), listOf(films, series, kids))
            val local = search.local("eclipse", indexed(), everywhere)
            assertEquals(1, local.size)
            assertEquals(emptyList(), search.complete(source(filmsHeld = listOf(amelie)), "eclipse", local))
        }

    @Test
    fun `recent searches - ten per profile, the latest first, once each, forgotten one by one or all`() =
        runTest {
            repeat(12) { index ->
                clock.now = clock.now.plusSeconds(1)
                search.remember(profile, "titre $index")
            }
            val recent = search.recent(profile).first()
            assertEquals(SearchRepository.RECENT, recent.size)
            assertEquals("titre 11", recent.first())
            assertEquals("titre 2", recent.last())

            clock.now = clock.now.plusSeconds(1)
            search.remember(profile, "Amélie")
            clock.now = clock.now.plusSeconds(1)
            search.remember(profile, "  amelie ")
            assertEquals("amelie", search.recent(profile).first().first())
            assertEquals(1, search.recent(profile).first().count { it.equals("amelie", ignoreCase = true) })

            // Another profile has its own.
            assertEquals(emptyList(), search.recent(ProfileKey(SERVER, "u2")).first())

            search.forget(profile, "AMÉLIE")
            assertTrue(search.recent(profile).first().none { it.contains("amelie") })
            search.remember(profile, " ")
            search.clearRecent(profile)
            assertEquals(emptyList(), search.recent(profile).first())
        }

    @Test
    fun `suggestions - the latest additions and the genres`() =
        runTest {
            val suggestions = search.suggestions(source(), listOf(films, series, kids))
            assertTrue(suggestions.recentlyAdded.isNotEmpty())
            assertTrue(suggestions.recentlyAdded.all { it.kind == ItemKind.Movie || it.kind == ItemKind.Series })
        }

    @Test
    fun `forgetting the server forgets its titles and its recent searches`() =
        runTest {
            titleIndex.sync(SERVER, source(), listOf(films, series, kids))
            search.remember(profile, "amelie")
            titleIndex.clear(SERVER)
            assertEquals(emptyList(), indexed())
            assertEquals(emptyList(), search.recent(profile).first())
        }

    private companion object {
        const val SERVER = "home"
        val ADDRESS = ServerAddress.of("http://192.0.2.10:8096")
    }
}
