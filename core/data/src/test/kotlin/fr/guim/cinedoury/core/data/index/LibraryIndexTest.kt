package fr.guim.cinedoury.core.data.index

import androidx.room.Room
import fr.guim.cinedoury.core.data.db.CineDatabase
import fr.guim.cinedoury.core.model.AudioFormat
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.EpisodeInfo
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.Library
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MediaSource
import fr.guim.cinedoury.core.model.MediaStream
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.TechBadge
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.model.VideoRange
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The local index on an in-memory Room, filled from an in-memory source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryIndexTest {
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
    private val index = LibraryIndex(database.index(), clock, pageSize = 2)

    private val films = Library(LibraryId("films"), "Films", LibraryKind.Movies)
    private val series = Library(LibraryId("series"), "Séries", LibraryKind.Shows)

    private fun video(
        width: Int,
        range: VideoRange,
        codec: String = "hevc",
    ) = MediaStream(0, StreamType.Video, codec, width = width, videoRange = range)

    private fun audio(
        codec: String,
        channels: Int,
        profile: String? = null,
        language: String = "fra",
    ) = MediaStream(1, StreamType.Audio, codec, language = language, channels = channels, profile = profile)

    private fun film(
        id: String,
        vararg streams: MediaStream,
        added: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) = MediaItem(
        id = ItemId(id),
        kind = ItemKind.Movie,
        title = id,
        dateAdded = added,
        mediaSources = listOf(MediaSource("$id-source", null, "mkv", 1_000, streams.toList())),
    )

    private val dune =
        film("dune", video(3840, VideoRange.DolbyVision), audio("truehd", 8, "Dolby TrueHD + Dolby Atmos", "eng"))
    private val amelie = film("amelie", video(1920, VideoRange.Sdr, "h264"), audio("ac3", 6))
    private val alien = film("alien", video(3840, VideoRange.Hdr10), audio("dts", 6, "DTS-HD MA"))
    private val marees = MediaItem(ItemId("marees"), ItemKind.Series, "Les Marées")
    private val episode =
        film("marees-1", video(1920, VideoRange.Hdr10), audio("eac3", 6)).copy(
            kind = ItemKind.Episode,
            episode = EpisodeInfo(ItemId("marees"), "Les Marées", ItemId("s1"), 1, 1),
        )

    private suspend fun source(vararg movies: MediaItem): LibrarySource {
        val fake =
            InMemorySource(
                libraries = listOf(films, series),
                contents = mapOf(films.id to movies.toList(), series.id to listOf(marees)),
                extras = listOf(episode),
            )
        return fake.open(fake.signIn(ADDRESS, Credentials.Password("admin", "password")))
    }

    @AfterTest
    fun close() = database.close()

    @Test
    fun `fills every film and episode, page by page, then filters on what Jellyfin cannot`() =
        runTest {
            index.sync(SERVER, source(dune, amelie, alien), listOf(films, series))
            val status = index.status(SERVER).first()
            assertEquals(3, status.films)
            assertEquals(1, status.episodes)
            assertTrue(status.ready)
            assertFalse(status.running)

            suspend fun matching(filters: ItemFilters) =
                index
                    .matching(
                        SERVER,
                        films.id,
                        filters,
                    )!!
                    .map { it.value }
                    .toSet()
            assertEquals(
                setOf("dune", "alien"),
                matching(ItemFilters(ranges = setOf(DynamicRange.DolbyVision, DynamicRange.Hdr10))),
            )
            assertEquals(setOf("dune"), matching(ItemFilters(audioFormats = setOf(AudioFormat.Atmos))))
            assertEquals(setOf("alien"), matching(ItemFilters(audioFormats = setOf(AudioFormat.Dts))))
            assertEquals(setOf("amelie"), matching(ItemFilters(videoCodecs = setOf(VideoCodec.H264))))
            // Filters combine: HDR10 and Atmos together match nothing.
            assertEquals(
                emptySet(),
                matching(ItemFilters(ranges = setOf(DynamicRange.Hdr10), audioFormats = setOf(AudioFormat.Atmos))),
            )
            assertNull(index.matching(SERVER, films.id, ItemFilters()))
            // A series matches through its episodes.
            assertEquals(
                setOf(ItemId("marees")),
                index.matching(SERVER, series.id, ItemFilters(ranges = setOf(DynamicRange.Hdr10))),
            )
        }

    @Test
    fun `the badges of a film, and of a series from its episodes`() =
        runTest {
            index.sync(SERVER, source(dune, amelie), listOf(films, series))
            assertEquals(
                listOf(TechBadge.Uhd, TechBadge.DolbyVision, TechBadge.Atmos, TechBadge.Surround71, TechBadge.Original),
                index.summary(SERVER, dune)!!.badges(),
            )
            assertEquals(
                listOf(TechBadge.FullHd, TechBadge.Hdr10, TechBadge.Surround51, TechBadge.LocalAudio),
                index.summary(SERVER, marees)!!.badges(),
            )
            assertNull(index.summary(SERVER, MediaItem(ItemId("unknown"), ItemKind.Movie, "?")))
        }

    @Test
    fun `follows the changes, and drops what the server deleted`() =
        runTest {
            index.sync(SERVER, source(dune, amelie, alien), listOf(films, series))
            clock.now = clock.now.plusSeconds(3_600)
            // « alien » was deleted on the server: the counts differ, the library is read whole.
            index.sync(SERVER, source(dune, amelie), listOf(films, series))
            assertEquals(2, index.status(SERVER).first().films)
            assertEquals(
                emptySet(),
                index.matching(SERVER, films.id, ItemFilters(audioFormats = setOf(AudioFormat.Dts))),
            )
            // A library the user cannot see any more leaves the index.
            index.sync(SERVER, source(dune, amelie), listOf(films))
            assertEquals(0, index.status(SERVER).first().episodes)
        }

    @Test
    fun `a query filtered by the index keeps the server's order and pages`() =
        runTest {
            val source = source(dune, amelie, alien)
            index.sync(SERVER, source, listOf(films, series))
            val query =
                ItemQuery(
                    films.id,
                    setOf(ItemKind.Movie),
                    ItemSort(SortField.Title),
                    ItemFilters(ranges = setOf(DynamicRange.DolbyVision, DynamicRange.Hdr10)),
                )
            val indexed = IndexedQuery(source, index, SERVER, query)
            assertTrue(indexed.usesIndex)
            val first = indexed.page(PageRequest(0, 1))
            assertEquals(2, first.totalCount)
            assertEquals(listOf("alien"), first.items.map { it.title })
            assertEquals(listOf("dune"), indexed.page(PageRequest(1, 1)).items.map { it.title })
            assertEquals(1, indexed.countBelow("b"))
            // Without a filter of the index, the server answers alone.
            assertFalse(IndexedQuery(source, index, SERVER, query.copy(filters = ItemFilters())).usesIndex)
        }

    private companion object {
        const val SERVER = "home"
        val ADDRESS = ServerAddress.of("http://192.0.2.10:8096")
    }
}
