package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.AudioFeature
import fr.guim.cinedoury.core.model.CreditType
import fr.guim.cinedoury.core.model.DynamicRange
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.ItemSort
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.SeriesStatus
import fr.guim.cinedoury.core.model.SmartPlay
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.StreamType
import fr.guim.cinedoury.core.model.TechnicalSummary
import fr.guim.cinedoury.core.model.VideoCodec
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * The details screens of J3 and the local index against the Jellyfin 12.3 of `ci/jellyfin`.
 * Skipped when JELLYFIN_URL is not set. Every change of user data is undone at the end.
 */
class JellyfinDetailsIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.0.0-test", deviceId = "cine-doury-integration-test", deviceName = "CI"),
        )

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    private suspend fun source(): LibrarySource {
        val username = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
        val password = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
        return connector.open(connector.signIn(ServerAddress.of(url), Credentials.Password(username, password)))
    }

    private suspend fun LibrarySource.find(
        library: String,
        kind: ItemKind,
        title: String,
    ): MediaItem {
        val id = libraries().single { it.name == library }.id
        return items(ItemQuery(id, setOf(kind)), PageRequest(limit = 100)).items.single { it.title == title }
    }

    @Test
    fun `a film with two versions`() =
        runTest {
            val source = source()
            val film = source.item(source.find("Films", ItemKind.Movie, "Double jeu").id)
            assertEquals(2, film.mediaSources.size, film.mediaSources.toString())
            val widths = film.mediaSources.mapNotNull { it.video?.width }.sorted()
            assertEquals(listOf(1280, 1920), widths)
            assertTrue(film.mediaSources.all { !it.path.isNullOrEmpty() })
        }

    @Test
    fun `a film with several audio and subtitle tracks, HDR10, and a local trailer`() =
        runTest {
            val source = source()
            val film = source.item(source.find("Films", ItemKind.Movie, "Nuit polaire").id)
            val streams = film.mediaSources.single().streams
            assertEquals(
                listOf("fra", "eng", "jpn"),
                streams.filter { it.type == StreamType.Audio }.map { it.language },
            )
            val subtitles = streams.filter { it.type == StreamType.Subtitle }
            assertEquals(4, subtitles.size)
            assertTrue(subtitles.single { it.isForced }.language == "fra")
            val summary = TechnicalSummary.of(film.mediaSources)
            assertEquals(setOf(DynamicRange.Hdr10), summary.ranges)
            assertEquals(setOf(VideoCodec.Hevc), summary.videoCodecs)
            assertTrue(AudioFeature.Surround51 in summary.audio)
            assertEquals(1, film.localTrailerCount)
            assertEquals(1, source.localTrailers(film.id).size)
        }

    @Test
    fun `credits, a trailer on the web and a saga in release order`() =
        runTest {
            val source = source()
            val film = source.item(source.find("Films", ItemKind.Movie, "Le Phare").id)
            val actor = film.people.first { it.type == CreditType.Actor }
            assertEquals("Camille Martin", actor.name)
            assertEquals("Yann, le gardien", actor.role)
            assertNotNull(actor.image, "the bootstrap gives the people a photo")
            assertTrue(film.people.any { it.type == CreditType.Director && it.name == "Marie Duval" })
            assertTrue(film.people.any { it.type == CreditType.Writer && it.name == "Claire Fontaine" })
            assertEquals("aqz-KE-bpKQ", film.remoteTrailers.single().youTubeId)
            assertNotNull(film.images.logo, "the bootstrap gives the film a logo")

            val saga = source.collectionsOf(film.id).single()
            assertEquals("Le Phare : la saga", saga.title)
            assertEquals(listOf("Le Phare", "Retour au phare"), source.collectionItems(saga.id).map { it.title })
        }

    @Test
    fun `similar films are in the library`() =
        runTest {
            val source = source()
            val film = source.find("Films", ItemKind.Movie, "Le Phare")
            val similar = source.similar(film.id, limit = 12)
            assertTrue(similar.isNotEmpty())
            assertTrue(similar.none { it.id == film.id })
            assertTrue(similar.all { it.kind == ItemKind.Movie })
        }

    @Test
    fun `a series with two seasons, and its smart button`() =
        runTest {
            val source = source()
            val series = source.item(source.find("Séries", ItemKind.Series, "Les Marées").id)
            assertEquals(SeriesStatus.Ended, series.seriesStatus)
            assertEquals(2016, series.endDate?.year)
            assertEquals(listOf(1, 2), source.seasons(series.id).map { it.episode?.seasonNumber })
            val episodes = source.episodes(series.id)
            assertEquals(listOf("Grande marée", "Mortes-eaux", "Le retour", "Le chenal"), episodes.map { it.title })
            // The bootstrap watched S01E01 and started S01E02.
            val play = SmartPlay.of(episodes)!!
            assertEquals("Mortes-eaux", play.episode.title)
            assertTrue(play.resume)
        }

    @Test
    fun `a series with a single season`() =
        runTest {
            val source = source()
            val series = source.find("Séries", ItemKind.Series, "Une saison")
            assertEquals(1, source.seasons(series.id).size)
            assertEquals(3, source.episodes(series.id).size)
        }

    @Test
    fun `marks watched and favourite, then back`() =
        runTest {
            val source = source()
            val film = source.find("Films", ItemKind.Movie, "Horizon perdu")
            try {
                assertTrue(source.setPlayed(film.id, played = true).played)
                assertTrue(source.item(film.id).userData!!.played)
                assertTrue(source.setFavorite(film.id, favorite = true).isFavorite)
                assertTrue(source.item(film.id).userData!!.isFavorite)
            } finally {
                assertFalse(source.setPlayed(film.id, played = false).played)
                assertFalse(source.setFavorite(film.id, favorite = false).isFavorite)
            }
            // A whole season.
            val season = source.seasons(source.find("Séries", ItemKind.Series, "Brume").id).single()
            val before = source.episodes(season.episode!!.seriesId!!).associate { it.id to it.userData!!.played }
            try {
                source.setPlayed(season.id, played = true)
                assertTrue(source.episodes(season.episode!!.seriesId!!).all { it.userData!!.played })
            } finally {
                before.forEach { (id, played) -> source.setPlayed(id, played) }
            }
        }

    @Test
    fun `a person and the filmography in the library`() =
        runTest {
            val source = source()
            val film = source.item(source.find("Films", ItemKind.Movie, "Le Phare").id)
            val person = source.item(film.people.first { it.name == "Marie Duval" }.id)
            assertEquals(ItemKind.Person, person.kind)
            val works = source.filmography(person.id)
            assertTrue(works.any { it.title == "Le Phare" })
            assertTrue(works.all { it.kind == ItemKind.Movie || it.kind == ItemKind.Series })
        }

    @Test
    fun `playback preferences of the profile`() =
        runTest {
            val preferences = source().playbackPreferences()
            assertTrue(preferences.playDefaultAudio)
        }

    @Test
    fun `the index reads every film and episode with its tracks, then the changes only`() =
        runTest {
            val source = source()
            val films = source.libraries().single { it.name == "Films" }.id
            val series = source.libraries().single { it.name == "Séries" }.id
            val (all, time) =
                measureTimedValue {
                    listOf(films, series).flatMap { library ->
                        val read = mutableListOf<MediaItem>()
                        var start: Int? = 0
                        while (start != null) {
                            val page =
                                source.technicalItems(
                                    library,
                                    changedSince = null,
                                    PageRequest(start, INDEX_PAGE),
                                )
                            read += page.items
                            start = page.nextStartIndex
                        }
                        read
                    }
                }
            println("Index: ${all.size} items read in $time")
            assertEquals(45, all.count { it.kind == ItemKind.Movie })
            assertEquals(11, all.count { it.kind == ItemKind.Episode })
            assertTrue(all.all { it.mediaSources.isNotEmpty() })
            assertTrue(all.filter { it.kind == ItemKind.Episode }.all { it.episode?.seriesId != null })
            val hdr = all.filter { DynamicRange.Hdr10 in TechnicalSummary.of(it.mediaSources).ranges }
            assertEquals(listOf("Nuit polaire"), hdr.map { it.title })

            val future = Instant.now().plus(1, ChronoUnit.DAYS)
            assertEquals(0, source.technicalItems(films, future, PageRequest()).totalCount)
        }

    @Test
    fun `ids of a query, then the items by id`() =
        runTest {
            val source = source()
            val films = source.libraries().single { it.name == "Films" }.id
            val query =
                ItemQuery(films, setOf(ItemKind.Movie), ItemSort(SortField.Title), ItemFilters(genres = setOf("Drame")))
            val ids = source.itemIds(query)
            val titles = source.items(query, PageRequest(limit = 100)).items.map { it.id }
            assertEquals(titles, ids)
            val chosen: List<ItemId> = ids.take(3)
            assertEquals(
                chosen.toSet(),
                source
                    .items(ItemQuery(ids = chosen), PageRequest())
                    .items
                    .map { it.id }
                    .toSet(),
            )
        }

    private companion object {
        const val INDEX_PAGE = 20
    }
}
