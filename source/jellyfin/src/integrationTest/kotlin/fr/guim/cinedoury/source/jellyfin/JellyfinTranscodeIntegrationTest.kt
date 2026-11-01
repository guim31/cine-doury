package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.PlayMethod
import fr.guim.cinedoury.core.model.PlaybackFallback
import fr.guim.cinedoury.core.model.PlaybackInfoRequest
import fr.guim.cinedoury.core.model.PlaybackProfiles
import fr.guim.cinedoury.core.model.PlaybackStream
import fr.guim.cinedoury.core.model.TranscodeReason
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.fake.SampleDevices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * What the server makes for a screen (O and P, `SPEC.md` § 4.29), against the Jellyfin 12.3 of
 * `ci/jellyfin`, with a film made for it: « Banquise », 3840 × 1608 in HEVC Main 10 HDR10, E-AC3 5.1, in
 * MKV (the film of the Chromecast). The `TranscodingUrl` and the ffmpeg command the server runs are read:
 * - a Chromecast on a 1080p SDR screen: encoded again **straight away** with tone mapping (no copy of
 *   the HDR10 first), no larger than 1920 × 1080, at 20 Mb/s at most, in H.264 High;
 * - a quality chosen by hand is not capped;
 * - on an HDR10 screen without an E-AC3 decoder, the video is still copied as it is, 4K;
 * - the Shield still plays the file directly.
 *
 * Needs JELLYFIN_URL, JELLYFIN_MEDIA_DIR (the folder the server reads as `/media`) and ffmpeg; skipped
 * otherwise. The film is deleted at the end, and its transcodings stopped.
 */
class JellyfinTranscodeIntegrationTest {
    private val sdr = PlaybackProfiles.build(SampleDevices.chromecastSdr)

    // A Chromecast on an HDR10 screen without a decoder for E-AC3: the server copies the video.
    private val hdr =
        PlaybackProfiles.build(SampleDevices.chromecastSdr.copy(display = SampleDevices.chromecast.display))
    private val shield = PlaybackProfiles.build(SampleDevices.shield)

    private suspend fun source(): LibrarySource {
        val connector =
            JellyfinConnector(
                ClientIdentity(appVersion = "0.0.0-test", deviceId = DEVICE, deviceName = "CI"),
            )
        return connector.open(connector.signIn(ServerAddress.of(url), Credentials.Password(USER, PASSWORD)))
    }

    private suspend fun LibrarySource.banquise(): MediaItem {
        val films = libraries().single { it.name == "Films" }.id
        val found = items(ItemQuery(films, setOf(ItemKind.Movie)), PageRequest(limit = 500)).items
        // Without a .nfo nor a provider, the title is the name of the folder (CLAUDE.md, « Pièges »).
        return item(found.single { it.title.startsWith(TITLE) }.id)
    }

    @Test
    fun `an SDR screen gets the HDR10 encoded again at once, tone mapped, 1080p, 20 Mbps, High`() =
        runTest(timeout = 3.minutes) {
            val source = source()
            val film = source.banquise()
            val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, sdr))
            assertEquals(PlayMethod.Transcode, stream.method, stream.url)
            assertTrue(TranscodeReason.VideoRangeTypeNotSupported in stream.reasons, stream.reasons.toString())
            assertFalse(PlaybackFallback.wantsTs(stream), "the video is encoded again: nothing to ask in TS")
            assertEquals("1920", stream.param("MaxWidth"))
            assertEquals("1080", stream.param("MaxHeight"))
            assertEquals("20000000", stream.param("VideoBitrate"))
            val command = ffmpeg(stream)
            assertFalse("-codec:v:0 copy" in command, command)
            assertTrue("tonemap" in command, command)
            assertTrue(Regex("""scale=[^ ]*1920[^ ]*1080""").containsMatchIn(command), command)
            // Jellyfin may pick less by itself (12.4 Mb/s here, from the file); never more.
            val maxrate =
                Regex("-maxrate (\\d+)")
                    .find(command)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            assertTrue(maxrate != null && maxrate <= 20_000_000, command)
            assertTrue("-profile:v:0 high" in command, command)
        }

    @Test
    fun `a quality chosen by hand is not capped`() =
        runTest(timeout = 3.minutes) {
            val source = source()
            val film = source.banquise()
            val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, sdr, maxBitrate = 40_000_000))
            assertEquals(PlayMethod.Transcode, stream.method)
            assertEquals("1920", stream.param("MaxWidth"))
            assertTrue((stream.param("VideoBitrate")?.toInt() ?: 0) > 20_000_000, stream.url)
        }

    @Test
    fun `on an HDR10 screen the 4K video is still copied, at its size`() =
        runTest(timeout = 3.minutes) {
            val source = source()
            val film = source.banquise()
            // The 12.3 offers direct play though the profile has no E-AC3 (as for DTS, G in M2): the
            // device falls back, without direct play, which this asks at once.
            val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, hdr, allowDirect = false))
            assertTrue(TranscodeReason.AudioCodecNotSupported in stream.reasons, "${stream.method} ${stream.url}")
            assertTrue(stream.reasons.none { it.encodesVideo }, stream.reasons.toString())
            val command = ffmpeg(stream)
            assertTrue("-codec:v:0 copy" in command, command)
            assertFalse("scale=" in command, command)
        }

    @Test
    fun `the Shield plays the file directly`() =
        runTest {
            val source = source()
            val film = source.banquise()
            val stream = source.playback.playbackInfo(PlaybackInfoRequest(film.id, shield))
            assertEquals(PlayMethod.DirectPlay, stream.method, stream.url)
        }

    private fun PlaybackStream.param(name: String): String? =
        url
            .substringAfter('?')
            .split('&')
            .map { it.substringBefore('=') to it.substringAfter('=') }
            .firstOrNull { it.first.equals(name, ignoreCase = true) }
            ?.second

    /** Starts the transcoding (the first segment), then reads the command in the log of the server. */
    private suspend fun ffmpeg(stream: PlaybackStream): String =
        withContext(Dispatchers.IO) {
            val headers = stream.headers
            val master = read(stream.url, headers)
            val base = stream.url.substringBefore("master.m3u8")
            val variant = master.lines().first { it.isNotBlank() && !it.startsWith("#") }
            val playlist = read(base + variant, headers)
            val segment = playlist.lines().first { it.isNotBlank() && !it.startsWith("#") }
            read(base + segment, headers)
            val playSession = checkNotNull(stream.playSessionId)
            try {
                // « FFmpeg.Transcode-<date>_<item>_<id>.log », « FFmpeg.DirectStream-… » for a copied video.
                val logs = Regex(""""Name":"(FFmpeg\.[A-Za-z]+-[^"]+)"""").findAll(read("$url/System/Logs", headers))
                logs
                    .map { it.groupValues[1] }
                    .sortedByDescending { it.substringAfter('-') }
                    .map { read("$url/System/Logs/Log?name=${URLEncoder.encode(it, "UTF-8")}", headers) }
                    .first { TITLE in it }
                    .lines()
                    .first { "ffmpeg" in it && " -i " in it }
            } finally {
                send("DELETE", "$url/Videos/ActiveEncodings?deviceId=$DEVICE&playSessionId=$playSession", headers)
            }
        }

    private fun read(
        address: String,
        headers: Map<String, String>,
    ): String {
        val connection = URI(address).toURL().openConnection() as HttpURLConnection
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.readTimeout = READ_TIMEOUT
        check(connection.responseCode in 200..299) { "$address answered ${connection.responseCode}" }
        return connection.inputStream.use { it.readBytes().decodeToString() }
    }

    private fun send(
        method: String,
        address: String,
        headers: Map<String, String>,
    ) {
        val connection = URI(address).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.responseCode
    }

    companion object {
        private val url = System.getenv("JELLYFIN_URL").orEmpty()
        private val media = System.getenv("JELLYFIN_MEDIA_DIR").orEmpty()
        private val USER = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin"
        private val PASSWORD = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test"
        private const val DEVICE = "cine-doury-transcode-test"
        private const val TITLE = "Banquise"
        private const val FOLDER = "films/Banquise (2021)"
        private const val READ_TIMEOUT = 60_000
        private const val FFMPEG_MINUTES = 5L

        private fun folder() = File(media, FOLDER)

        /** Makes the film, then a scan. */
        @BeforeClass
        @JvmStatic
        fun makeFilm() {
            assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
            assumeTrue("JELLYFIN_MEDIA_DIR is not set", media.isNotBlank())
            val ffmpeg = runCatching { ProcessBuilder("ffmpeg", "-version").start().waitFor() == 0 }
            assumeTrue("no ffmpeg", ffmpeg.getOrDefault(false))
            val folder = folder().apply { mkdirs() }
            val command = ffmpegCommand(File(folder, "Banquise (2021).mkv"))
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            check(process.waitFor(FFMPEG_MINUTES, TimeUnit.MINUTES) && process.exitValue() == 0) {
                process.inputStream.readBytes().decodeToString()
            }
            scan()
            // The scan may end before the film is in the library: wait for it.
            kotlinx.coroutines.runBlocking {
                val test = JellyfinTranscodeIntegrationTest()
                val source = test.source()
                repeat(SCAN_TRIES) {
                    if (runCatching { with(test) { source.banquise() } }.isSuccess) return@runBlocking
                    Thread.sleep(SCAN_LOOK)
                }
                error("$TITLE never came into the library")
            }
        }

        /** 12 s, two pictures a second: a few seconds of x265 for a 4K HDR10 film with E-AC3 5.1. */
        private fun ffmpegCommand(output: File): List<String> {
            val x265 =
                "log-level=error:hdr10=1:repeat-headers=1:colorprim=bt2020:transfer=smpte2084:" +
                    "colormatrix=bt2020nc:master-display=G(13250,34500)B(7500,3000)R(34000,16000)" +
                    "WP(15635,16450)L(10000000,1):max-cll=1000,400"
            val input = "-f lavfi -i testsrc2=size=3840x1608:rate=2:duration=12"
            val silence = "-f lavfi -i anullsrc=channel_layout=5.1:sample_rate=48000 -t 12 -map 0:v -map 1:a"
            val video = "-vf format=yuv420p10le -c:v libx265 -preset ultrafast -pix_fmt yuv420p10le"
            val colours = "-color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -c:a eac3 -b:a 384k"
            return listOf("ffmpeg", "-y", "-loglevel", "error") + "$input $silence $video".split(' ') +
                listOf("-x265-params", x265) + colours.split(' ') + output.path
        }

        @AfterClass
        @JvmStatic
        fun deleteFilm() {
            if (url.isBlank() || media.isBlank() || !folder().exists()) return
            folder().deleteRecursively()
            scan()
        }

        /** Scans the films and waits for the scheduled task to end. */
        private fun scan() =
            kotlinx.coroutines.runBlocking {
                val source = JellyfinTranscodeIntegrationTest().source()
                val films = source.libraries().single { it.name == "Films" }.id
                source.manage.scan(films, LibraryScan.NewFiles)
                Thread.sleep(SCAN_START)
                repeat(SCAN_TRIES) {
                    if (!source.manage.scanStatus().running) return@runBlocking
                    Thread.sleep(SCAN_LOOK)
                }
                error("the scan did not end")
            }

        private const val SCAN_START = 1_000L
        private const val SCAN_LOOK = 500L
        private const val SCAN_TRIES = 240
    }
}
