package fr.guim.cinedoury.core.data.manage

import androidx.room.Room
import fr.guim.cinedoury.core.data.db.CineDatabase
import fr.guim.cinedoury.core.data.home.HomeRepository
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.home.Samples
import fr.guim.cinedoury.core.data.home.Samples.film
import fr.guim.cinedoury.core.data.index.LibraryIndex
import fr.guim.cinedoury.core.model.DeletionPreview
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.Session
import fr.guim.cinedoury.core.source.fake.InMemorySource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** What a deletion leaves of an item in the app: nothing in the home, nothing in the index (`SPEC.md` § 4.12). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManageRepositoryTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CineDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val home = HomeRepository(database.homeCache(), database.preferences())
    private val index = LibraryIndex(database.index())
    private val updates = ManageUpdates()
    private val repository = ManageRepository(home, index, updates)
    private val profile = ProfileKey("home", "id-admin")

    private fun MediaItem.withFile() =
        copy(
            mediaSources = listOf(MediaSource("${id.value}-file", null, "mkv", 1_000, emptyList())),
        )

    private val started = film("started", 2001, 20.minutes, Instant.now()).withFile()
    private val kept = film("kept", 2024, added = Instant.parse("2026-10-20T00:00:00Z")).withFile()
    private val marees = MediaItem(ItemId("marees"), ItemKind.Series, "Les Marées")
    private val episode =
        film("marees-1", 2015, 10.minutes, Instant.now()).withFile().copy(
            kind = ItemKind.Episode,
            episode = EpisodeInfo(ItemId("marees"), "Les Marées", ItemId("s1"), 1, 1),
        )
    private val source =
        InMemorySource(
            libraries = listOf(Samples.films, Samples.series),
            contents = mapOf(Samples.films.id to listOf(started, kept), Samples.series.id to listOf(marees)),
            extras = listOf(episode),
        )
    private val library =
        source.open(
            Session(ServerAddress.of("http://192.0.2.10:8096"), "home", source.user("admin"), "t"),
        )

    @AfterTest
    fun close() = database.close()

    @Test
    fun `a deleted film leaves the rows of the home and the index at once, and the screens hear of it`() =
        runTest {
            home.refresh(profile, library)
            index.sync("home", library, listOf(Samples.films, Samples.series))
            assertEquals(listOf("started"), home.rows(profile).first()["resume"]?.map { it.title })
            assertNotNull(database.index().item("home", "started"))
            val heard = async(start = CoroutineStart.UNDISPATCHED) { updates.updates.first() }

            repository.deleted("home", DeletionPreview(started, listOf("/media/started.mkv"), 1_000))

            val rows = home.rows(profile).first()
            assertEquals(null, rows["resume"], "the only card of « Reprendre » is gone")
            assertEquals(listOf("kept"), rows["latest:films"]?.map { it.title })
            assertNull(database.index().item("home", "started"))
            assertNotNull(database.index().item("home", "kept"))
            assertEquals(ItemChange.Deleted(started.id), heard.await())
        }

    @Test
    fun `a deleted series takes its episodes out of the index`() =
        runTest {
            index.sync("home", library, listOf(Samples.films, Samples.series))
            assertNotNull(database.index().item("home", "marees-1"))
            val heard = async(start = CoroutineStart.UNDISPATCHED) { updates.updates.first() }

            repository.deleted("home", DeletionPreview(marees, emptyList(), null, listOf(episode.id)))

            assertNull(database.index().item("home", "marees-1"))
            val change = heard.await() as ItemChange.Deleted
            assertEquals(true, episode.id in change)
        }

    @Test
    fun `a changed item is announced`() =
        runTest {
            val heard = async(start = CoroutineStart.UNDISPATCHED) { updates.updates.first() }
            repository.changed(kept.id)
            assertEquals(ItemChange.Changed(kept.id), heard.await())
        }
}
