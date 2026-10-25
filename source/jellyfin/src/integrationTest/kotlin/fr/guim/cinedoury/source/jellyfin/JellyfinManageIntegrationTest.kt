package fr.guim.cinedoury.source.jellyfin

import fr.guim.cinedoury.core.model.IdentifyMatch
import fr.guim.cinedoury.core.model.IdentifySearch
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.ItemQuery
import fr.guim.cinedoury.core.model.LibraryId
import fr.guim.cinedoury.core.model.LibraryScan
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.MetadataRefresh
import fr.guim.cinedoury.core.model.PageRequest
import fr.guim.cinedoury.core.model.SearchTitle
import fr.guim.cinedoury.core.source.Credentials
import fr.guim.cinedoury.core.source.LibrarySource
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.core.source.SourceLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Management (J6) against the Jellyfin 12.3 of `ci/jellyfin`. Skipped when JELLYFIN_URL is not set;
 * the tests that need a film made for them also need JELLYFIN_MEDIA_DIR, the folder the server reads
 * as `/media` (the CI sets both).
 *
 * The test server has no metadata provider (`bootstrap.sh`): a search for an identity may come back
 * empty, so applying one is tested with a match built here; the providers' galleries are empty too,
 * so a new picture is one the server downloads from itself. Everything a test creates is deleted at
 * its end.
 */
class JellyfinManageIntegrationTest {
    private val url = System.getenv("JELLYFIN_URL").orEmpty()
    private val media = System.getenv("JELLYFIN_MEDIA_DIR").orEmpty()
    private val lines = mutableListOf<String>()
    private val connector =
        JellyfinConnector(
            ClientIdentity(appVersion = "0.0.0-test", deviceId = "cine-doury-integration-test", deviceName = "CI"),
            log = SourceLog { lines += it },
        )

    @BeforeTest
    fun requireServer() {
        assumeTrue("JELLYFIN_URL is not set", url.isNotBlank())
    }

    private suspend fun source(
        username: String = System.getenv("JELLYFIN_ADMIN_USER") ?: "admin",
        password: String = System.getenv("JELLYFIN_ADMIN_PASSWORD") ?: "cinedoury-test",
    ): LibrarySource = connector.open(connector.signIn(ServerAddress.of(url), Credentials.Password(username, password)))

    private suspend fun LibrarySource.films(): LibraryId = libraries().single { it.name == "Films" }.id

    private suspend fun LibrarySource.movies(): List<MediaItem> =
        items(ItemQuery(films(), setOf(ItemKind.Movie)), PageRequest(limit = 500)).items

    /** A real wait: `runTest` skips its virtual delays, the server does not. */
    private suspend fun pause(millis: Long) = withContext(Dispatchers.IO) { Thread.sleep(millis) }

    /** Starts a scan of the new files and waits for the scheduled task to finish. */
    private suspend fun LibrarySource.scanAndWait() {
        manage.scan(films(), LibraryScan.NewFiles)
        pause(1_000)
        repeat(120) {
            if (!manage.scanStatus().running) return
            pause(500)
        }
        error("the scan did not end")
    }

    private fun send(
        method: String,
        path: String,
        source: LibrarySource,
    ): Int {
        val connection = URI("$url$path").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        source.playback.streamHeaders.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection.responseCode
    }

    @Test
    fun `the rights of the administrator and of the child profile`() =
        runTest {
            val admin = source().manage.rights()
            assertTrue(admin.canManage)
            assertTrue(admin.canDeleteSomewhere, "bootstrap.sh gives the administrator the right to delete")
            val child = source("enfant", "")
            assertFalse(child.manage.rights().canManage)
            val film = child.movies().first()
            assertFailsWith<SourceException.Forbidden> { child.manage.refresh(film.id, MetadataRefresh.FillMissing) }
        }

    @Test
    fun `the providers of a film, and their galleries`() =
        runTest {
            val source = source()
            val film = source.movies().single { it.title == "Givre" }
            val providers = source.manage.identifyProviders(film.id, ItemKind.Movie)
            assertEquals("TheMovieDb", providers.first())
            val images = source.manage.remoteImages(film.id, ImageType.Primary, allLanguages = true)
            assertTrue("TheMovieDb" in images.providers, images.providers.toString())
            assertTrue(
                lines.any { it.startsWith("manage images item=${film.id.value}") && it.endsWith("ok") },
                "$lines",
            )
            assertTrue(lines.none { "Token" in it || "/media/" in it })
        }

    @Test
    fun `the scan of new files shows its progress and ends`() =
        runTest(timeout = 2.minutes) {
            val source = source()
            source.manage.scan(source.films(), LibraryScan.NewFiles)
            var seen = false
            repeat(120) {
                val status = source.manage.scanStatus()
                if (status.running) seen = true
                if (seen && !status.running) return@runTest
                pause(250)
            }
            assertTrue(seen, "the scheduled task never ran")
        }

    @Suppress("LongMethod") // One film, made and deleted once: every step in order.
    @Test
    fun `a misnamed film is a problem, then identified, a new poster, its file gone, then deleted`() =
        runTest(timeout = 5.minutes) {
            assumeTrue("JELLYFIN_MEDIA_DIR is not set", media.isNotBlank())
            val folder = File(media, "films/$MISNAMED")
            val aside = File(media, "aside-$MISNAMED")
            val source = source()
            try {
                folder.mkdirs()
                File(
                    media,
                    "films/Givre (2012)/Givre (2012).mkv",
                ).copyTo(File(folder, "$MISNAMED.mkv"), overwrite = true)
                source.scanAndWait()
                val film = source.item(source.movies().single { "Phrae" in it.title }.id)
                val library = source.films()

                // Refreshed in every mode: on the test server, which has no provider, « Tout remplacer »
                // empties the metadata of a film (CLAUDE.md, « Pièges »), hence a film made for it.
                MetadataRefresh.entries.forEach { source.manage.refresh(film.id, it) }
                pause(3_000)
                // Without providers the answer is empty; in the CI, which reaches TMDb, it may not be.
                val matches = source.manage.searchIdentity(film.id, IdentifySearch(ItemKind.Movie, "Le Phare", 2001))
                assertTrue(matches.all { it.title.isNotBlank() })

                // Unidentified: no provider id. The search starts from the cleaned folder name.
                assertTrue(
                    source.manage
                        .problems(library)
                        .unidentified
                        .any { it.id == film.id },
                )
                assertEquals(SearchTitle("Le Phrae", 2001), SearchTitle.of(film.title, film.year, 2026))
                val match =
                    IdentifyMatch(
                        title = "Le Phare",
                        year = 2001,
                        overview = null,
                        imageUrl = null,
                        provider = "TheMovieDb",
                        providerIds = mapOf("Tmdb" to "999999001"),
                    )
                source.manage.applyIdentity(film.id, match, replaceImages = false)
                assertEquals("999999001", source.item(film.id).providerIds["Tmdb"])
                assertFalse(
                    source.manage
                        .problems(library)
                        .unidentified
                        .any { it.id == film.id },
                )

                // Without poster, then a poster the server downloads (from itself: no provider here).
                assertTrue(send("DELETE", "/Items/${film.id.value}/Images/Primary", source) in 200..299)
                assertTrue(
                    source.manage
                        .problems(library)
                        .withoutPoster
                        .any { it.id == film.id },
                )
                val other = source.movies().single { it.title == "Le Phare" }
                source.manage.setImage(
                    film.id,
                    ImageType.Primary,
                    "http://localhost:8096/Items/${other.id.value}/Images/Primary",
                )
                assertNotNull(source.item(film.id).images.primary)

                // The file moved away before a scan: missing; back, it is not.
                folder.renameTo(aside)
                assertTrue(source.manage.missingFiles(library).any { it.id == film.id })
                aside.renameTo(folder)
                var progress = 0 to 0
                assertFalse(
                    source.manage.missingFiles(library) { done, total -> progress = done to total }.any {
                        it.id ==
                            film.id
                    },
                )
                assertEquals(progress.second, progress.first)

                // What a deletion removes, then the deletion: the item and its folder are gone.
                val preview = source.manage.deletionPreview(film)
                assertEquals(listOf("/media/films/$MISNAMED/$MISNAMED.mkv"), preview.paths)
                assertTrue((preview.sizeBytes ?: 0) > 0)
                source.manage.delete(film.id)
                assertFailsWith<SourceException.NotFound> { source.item(film.id) }
                assertFalse(File(folder, "$MISNAMED.mkv").exists())
            } finally {
                aside.deleteRecursively()
                if (folder.exists()) {
                    folder.deleteRecursively()
                    source.scanAndWait()
                }
            }
        }

    private companion object {
        /** A folder name as a download leaves it: misspelt, with dots, year and tags. */
        const val MISNAMED = "Le.Phrae.2001.1080p"
    }
}
