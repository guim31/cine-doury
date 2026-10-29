package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlaybackReport
import fr.guim.cinedoury.core.model.SegmentType
import fr.guim.cinedoury.core.model.SubtitleMethod
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.fake.SampleDevices
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Playback of J4 against the Jellyfin 12.3 of `ci/jellyfin` (`SPEC.md` § 4.11): the decision for
 * each profile, the reports, « vu », the segments. Skipped when JELLYFIN_URL is not set. Every
 * change of user data is undone at the end.
 */
class JellyfinPlaybackIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.0.0-test", deviceId = "cine-doury-playback-test", deviceName = "CI"),
        )
    private val full = PlaybackProfiles.build(SampleDevices.shield)
    private val limited = PlaybackProfiles.build(SampleDevices.standard)

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    private suspend fun source(): LibrarySource {
        val username = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
        val password = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
        return connector.open(connector.signIn(ServerAddress.of(url), Credentials.Password(username, password)))
    }

    private suspend fun LibrarySource.film(title: String): MediaItem {
        val id = libraries().single { it.name == "Films" }.id
        val found =
            items(ItemQuery(id, setOf(ItemKind.Movie)), PageRequest(limit = 100)).items.single {
                it.title ==
                    title
            }
        return item(found.id)
    }

    /**
     * Puts back the last played date of [item] (a report changes it, and the sort tests of
     * `JellyfinBrowseIntegrationTest` read it); a date long ago for an item never played.
     */
    private fun LibrarySource.restoreLastPlayed(item: MediaItem) {
        val date = item.userData?.lastPlayed?.toString() ?: "2000-01-01T00:00:00Z"
        val body = "{\"LastPlayedDate\":\"$date\"}"
        val address = "$url/UserItems/${item.id.value}/UserData?userId=${session.user.id.value}"
        val connection = URI(address).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        playback.streamHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray()) }
        check(connection.responseCode in 200..299) { "UserData answered ${connection.responseCode}" }
    }

    private fun get(
        address: String,
        headers: Map<String, String>,
    ): Pair<Int, String> {
        val connection = URI(address).toURL().openConnection() as HttpURLConnection
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.setRequestProperty("Range", "bytes=0-2047")
        return connection.responseCode to connection.inputStream.use { it.readNBytes(2048).decodeToString() }
    }

    @Test
    fun `the full profile plays HEVC HDR10 with E-AC3 directly, every subtitle in the file`() =
        runTest {
            val source = source()
            val film = source.film("Nuit polaire")
            assertEquals(listOf("Intro", "Film", "Credits"), film.chapters.map { it.name })
            val stream =
                source.playback.playbackInfo(
                    PlaybackInfoRequest(film.id, full, audioIndex = 1, subtitleIndex = 6),
                )
            assertEquals(PlayMethod.DirectPlay, stream.method, stream.toString())
            assertTrue(stream.reasons.isEmpty())
            assertFalse(stream.isHls)
            assertTrue(stream.subtitles.all { it.method == SubtitleMethod.Embed }, stream.subtitles.toString())
            assertTrue(stream.headers.getValue("Authorization").contains("Token="))
            val (status, _) = get(stream.url, stream.headers)
            assertTrue(status in 200..206, "stream answers $status")
        }

    @Test
    fun `the limited profile gets an HLS transcode and says why`() =
        runTest {
            val source = source()
            val film = source.film("Nuit polaire")
            val stream =
                source.playback.playbackInfo(
                    PlaybackInfoRequest(film.id, limited, audioIndex = 1, subtitleIndex = 7),
                )
            assertEquals(PlayMethod.Transcode, stream.method)
            assertTrue(stream.isHls)
            assertTrue(TranscodeReason.VideoCodecNotSupported in stream.reasons, stream.reasons.toString())
            assertEquals(SubtitleMethod.Encode, stream.delivery(7)?.method, "PGS drawn into the picture")
            assertEquals(SubtitleMethod.External, stream.delivery(5)?.method, "SRT served apart")
            val (status, body) = get(stream.url, stream.headers)
            assertEquals(200, status)
            assertTrue(body.startsWith("#EXTM3U"), body)
            source.playback.reportStopped(report(film, stream.playSessionId, 0.seconds, PlayMethod.Transcode))
            source.restoreLastPlayed(film)
        }

    @Test
    fun `after a failure of direct play the server transcodes`() =
        runTest {
            val source = source()
            val film = source.film("Le Phare")
            val direct = source.playback.playbackInfo(PlaybackInfoRequest(film.id, full))
            assertEquals(PlayMethod.DirectPlay, direct.method)
            val fallback =
                source.playback.playbackInfo(
                    PlaybackInfoRequest(film.id, full, start = 2.seconds, allowDirect = false),
                )
            assertEquals(PlayMethod.Transcode, fallback.method)
            assertTrue(fallback.isHls)
            source.playback.reportStopped(report(film, fallback.playSessionId, 2.seconds, PlayMethod.Transcode))
            source.restoreLastPlayed(film)
        }

    @Test
    fun `a progress report moves the resume point, and a stop near the end marks the film watched`() =
        runTest {
            val source = source()
            val film = source.film("Zone blanche")
            try {
                val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, full))
                val playback = source.playback
                playback.reportStart(report(film, stream.playSessionId, 0.seconds, stream.method))
                playback.reportProgress(report(film, stream.playSessionId, 20.minutes, stream.method))
                playback.reportStopped(report(film, stream.playSessionId, 20.minutes, stream.method))
                val resumed = source.item(film.id).userData
                assertEquals(20L, resumed?.playbackPosition?.inWholeMinutes, resumed.toString())
                assertFalse(resumed?.played == true)

                val again = playback.playbackInfo(PlaybackInfoRequest(film.id, full, start = 20.minutes))
                val end = film.runtime!! * 0.95
                playback.reportStart(report(film, again.playSessionId, 20.minutes, again.method))
                playback.reportStopped(report(film, again.playSessionId, end, again.method))
                assertTrue(source.item(film.id).userData?.played == true)
            } finally {
                source.setPlayed(film.id, false)
                source.restoreLastPlayed(film)
            }
        }

    @Test
    fun `clearing the progress takes a film out of resume without marking it watched`() =
        runTest {
            val source = source()
            val film = source.film("Zone blanche")
            try {
                val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, full))
                val playback = source.playback
                playback.reportStart(report(film, stream.playSessionId, 0.seconds, stream.method))
                playback.reportStopped(report(film, stream.playSessionId, 20.minutes, stream.method))
                assertTrue(source.resume(null, 50).any { it.id == film.id }, "the film is in resume")

                val data = source.clearProgress(film.id)
                assertEquals(0L, data.playbackPosition.inWholeSeconds, data.toString())
                assertFalse(data.played)
                assertFalse(source.item(film.id).userData?.played == true)
                assertFalse(source.resume(null, 50).any { it.id == film.id }, "the film left resume")
            } finally {
                source.clearProgress(film.id)
                source.restoreLastPlayed(film)
            }
        }

    @Test
    fun `segments are read, from the chapters when Chapter Segments Provider is installed`() =
        runTest {
            val source = source()
            val film = source.film("Le Phare")
            val segments = source.playback.segments(film.id)
            if (System.getenv("JELLYFIN_SEGMENTS") == "true") {
                assertTrue(segments.any { it.type == SegmentType.Intro && it.start == 0.seconds }, segments.toString())
                assertTrue(segments.any { it.type == SegmentType.Outro }, segments.toString())
            } else {
                println("Chapter Segments Provider not installed: $segments")
            }
        }

    private fun report(
        item: MediaItem,
        session: String?,
        position: kotlin.time.Duration,
        method: PlayMethod,
    ) = PlaybackReport(
        item = item.id,
        sourceId = item.mediaSources.first().id,
        playSessionId = session,
        position = position,
        paused = false,
        method = method,
        audioIndex = null,
        subtitleIndex = null,
    )
}
