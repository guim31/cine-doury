package fr.guim.cinedoury.core.data.home

import androidx.room.Room
import fr.guim.cinedoury.core.data.db.CineDatabase
import fr.guim.cinedoury.core.data.home.Samples.film
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.LibraryStateRepository
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.model.HomeExclusions
import fr.guim.cinedoury.core.model.ImageRef
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemImages
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.WatchStatus
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

/** The cache of the home and the preferences of the libraries, on an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeRepositoryTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CineDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val repository = HomeRepository(database.homeCache(), database.preferences())
    private val profile = ProfileKey("home", "id-admin")
    private val poster = ImageRef(ItemId("new"), ImageType.Primary, "tag", blurHash = "L|AB,;:%")
    private val newFilm =
        film("new", 2024, added = Instant.parse("2026-10-20T00:00:00Z"))
            .copy(images = ItemImages(primary = poster), genres = listOf("Drame", "Comédie"))
    private val source =
        InMemorySource(
            libraries = Samples.all,
            contents =
                mapOf(
                    Samples.films.id to listOf(film("started", 2001, 20.minutes, Instant.now()), newFilm),
                    Samples.series.id to emptyList(),
                    Samples.shows.id to listOf(film("hidden", 2025)),
                    Samples.collections.id to emptyList(),
                ),
            exclusions = HomeExclusions(fromHome = setOf(Samples.shows.id)),
        )
    private val session = Session(ServerAddress.of("http://192.0.2.10:8096"), "home", source.user("admin"), "t")

    @AfterTest
    fun close() = database.close()

    @Test
    fun `the cache is empty, then holds every row after a refresh`() =
        runTest {
            assertEquals(emptyMap(), repository.rows(profile).first())
            repository.refresh(profile, source.open(session))
            val rows = repository.rows(profile).first()
            assertEquals(listOf("started"), rows["resume"]?.map { it.title })
            assertEquals(listOf("new", "started"), rows["latest:films"]?.map { it.title })
            assertEquals(null, rows["latest:spectacles"], "excluded from the home")
            // The item comes back from the cache as the source gave it.
            assertEquals(newFilm, rows.getValue("latest:films").first())
            val libraries = repository.libraries(profile).first()
            assertEquals(Samples.all, libraries.libraries)
            assertEquals(setOf(Samples.shows.id), libraries.exclusions.fromHome)
        }

    @Test
    fun `a failed refresh keeps the cache`() =
        runTest {
            repository.refresh(profile, source.open(session))
            source.unreachable += session.server
            assertFailsWith<SourceException.Unreachable> { repository.refresh(profile, source.open(session)) }
            assertEquals(listOf("started"), repository.rows(profile).first()["resume"]?.map { it.title })
        }

    @Test
    fun `the layout is the default until changed, and per profile`() =
        runTest {
            assertEquals(HomeLayouts.DEFAULT, repository.layout(profile).first())
            val changed = HomeLayouts.remove(HomeLayouts.moveUp(HomeLayouts.DEFAULT, "latest"), "resume")
            repository.saveLayout(profile, changed)
            assertEquals(changed, repository.layout(profile).first())
            assertEquals(HomeLayouts.DEFAULT, repository.layout(ProfileKey("home", "other")).first())
            repository.resetLayout(profile)
            assertEquals(HomeLayouts.DEFAULT, repository.layout(profile).first())
        }

    @Test
    fun `a library can leave the rail and come back`() =
        runTest {
            repository.setShownInRail(profile, Samples.films.id, shown = false)
            assertEquals(setOf(Samples.films.id), repository.railHidden(profile).first())
            repository.setShownInRail(profile, Samples.films.id, shown = true)
            assertEquals(emptySet(), repository.railHidden(profile).first())
        }

    @Test
    fun `signing out forgets the cache, forgetting the server forgets the preferences too`() =
        runTest {
            repository.refresh(profile, source.open(session))
            repository.saveLayout(profile, HomeLayouts.remove(HomeLayouts.DEFAULT, "resume"))
            repository.clear("home", "id-admin")
            assertEquals(emptyMap(), repository.rows(profile).first())
            assertEquals(3, repository.layout(profile).first().size)
            repository.clear("home", null)
            assertEquals(HomeLayouts.DEFAULT, repository.layout(profile).first())
        }

    @Test
    fun `a library remembers its state`() =
        runTest {
            val states = LibraryStateRepository(database.preferences())
            assertEquals(LibraryState(), states.load(profile, Samples.films.id))
            val state =
                LibraryState(
                    view = LibraryView.List,
                    sort = ItemSort(SortField.DateAdded),
                    filters = ItemFilters(watch = WatchStatus.Unwatched, genres = setOf("Drame")),
                    groupCollections = true,
                    position = 31,
                )
            states.save(profile, Samples.films.id, state)
            assertEquals(state, states.load(profile, Samples.films.id))
            assertEquals(LibraryState(), states.load(profile, Samples.series.id))
        }
}
