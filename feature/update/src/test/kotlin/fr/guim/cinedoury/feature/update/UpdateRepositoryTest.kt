package fr.guim.cinedoury.feature.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.guim.cinedoury.core.data.prefs.AppPreferences
import fr.guim.cinedoury.core.update.Checksums
import fr.guim.cinedoury.core.update.ReleaseClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The update from the check to Android (`SPEC.md` § 4.18), against a fake GitHub and a fake Android:
 * the permission, the SHA-256, the signature, an ignored version, a development build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val server = MockWebServer()
    private val apk = "the APK of 1.2.0".toByteArray()
    private var published = Checksums.sha256(apk)
    private val now = Instant.parse("2026-10-26T10:00:00Z")

    private val gate =
        object : PackageGate {
            var allowed = true
            var installedKey = "release-key"
            var apkKey = "release-key"
            val installed = mutableListOf<File>()
            val started = CompletableDeferred<File>()
            var settingsOpened = 0

            override fun canInstall() = allowed

            override fun installedSigners() = setOf(installedKey)

            override fun archiveSigners(apk: File) = setOf(apkKey)

            override suspend fun install(apk: File): InstallResult {
                installed += apk
                started.complete(apk)
                return InstallResult.Started
            }

            override fun openUnknownSourcesSettings() {
                settingsOpened++
            }

            override fun openAppDetails() = Unit
        }

    private lateinit var preferences: AppPreferences

    @BeforeTest
    fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.url.encodedPath) {
                        "/repos/guim31/cine-doury/releases" -> {
                            MockResponse.Builder().body(releases()).build()
                        }

                        "/cine-doury-1.2.0.apk" -> {
                            MockResponse.Builder().body(Buffer().write(apk)).build()
                        }

                        "/cine-doury-1.2.0.apk.sha256" -> {
                            MockResponse
                                .Builder()
                                .body(
                                    "$published  cine-doury-1.2.0.apk",
                                ).build()
                        }

                        else -> {
                            MockResponse.Builder().code(404).build()
                        }
                    }
            }
        server.start()
        preferences =
            AppPreferences(
                PreferenceDataStoreFactory.create(
                    scope = scope.backgroundScope,
                ) { folder.root.resolve("settings.preferences_pb") },
            )
    }

    @AfterTest
    fun stop() = server.close()

    private fun releases() =
        """[{"tag_name":"v1.2.0","name":"Ciné Doury 1.2.0","draft":false,"prerelease":false,
            "published_at":"2026-10-25T10:00:00Z","body":"- Réglages",
            "assets":[{"name":"cine-doury-1.2.0.apk","browser_download_url":"${server.url(
            "/cine-doury-1.2.0.apk",
        )}","size":16},
            {"name":"cine-doury-1.2.0.apk.sha256","browser_download_url":"${server.url(
            "/cine-doury-1.2.0.apk.sha256",
        )}","size":90}]}]"""

    /** The download and its checks run on the threads of OkHttp: wait for their outcome. */
    private suspend fun UpdateRepository.afterInstall(): UpdatePhase =
        state.first { it.phase !is UpdatePhase.Available && it.phase !is UpdatePhase.Downloading }.phase

    private fun repository(version: String = "1.1.0") =
        UpdateRepository(
            client = ReleaseClient(api = server.url("/").toString()),
            preferences = preferences,
            gate = gate,
            versionName = version,
            downloads = folder.newFolder(),
            scope = scope.backgroundScope,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

    @Test
    fun `at start, once a day, the new version is offered`() =
        scope.runTest {
            val updates = repository()
            assertTrue(updates.checkAtStart())
            assertIs<UpdatePhase.Available>(updates.state.value.phase)
            assertEquals(now, preferences.updates.first().lastCheck)
            // The same day: no request, nothing offered.
            val requests = server.requestCount
            assertFalse(repository().checkAtStart())
            assertEquals(requests, server.requestCount)
        }

    @Test
    fun `installed after the download and its checks, by Android`() =
        scope.runTest {
            val updates = repository()
            updates.checkAtStart()
            updates.install()
            assertIs<UpdatePhase.Ready>(updates.afterInstall())
            updates.confirm()
            val given = gate.started.await()
            assertEquals("cine-doury-1.2.0.apk", given.name)
            assertTrue(given.readBytes().contentEquals(apk))
        }

    @Test
    fun `without the permission, the screen leads to it, then goes on`() =
        scope.runTest {
            gate.allowed = false
            val updates = repository()
            updates.checkAtStart()
            updates.install()
            assertIs<UpdatePhase.PermissionMissing>(updates.state.value.phase)
            updates.openPermissionSettings()
            assertEquals(1, gate.settingsOpened)
            gate.allowed = true
            updates.permissionMaybeGiven()
            assertIs<UpdatePhase.Ready>(updates.state.first { it.phase is UpdatePhase.Ready }.phase)
        }

    @Test
    fun `a file that is not the one published is never installed`() =
        scope.runTest {
            published = "0".repeat(64)
            val updates = repository()
            updates.checkAtStart()
            updates.install()
            assertEquals(UpdateFailure.Checksum, (updates.afterInstall() as UpdatePhase.Failed).error)
            updates.confirm()
            assertTrue(gate.installed.isEmpty())
        }

    @Test
    fun `another key than the installed app is told before installing`() =
        scope.runTest {
            gate.installedKey = "debug-key"
            val updates = repository()
            updates.checkAtStart()
            updates.install()
            assertIs<UpdatePhase.SignatureMismatch>(updates.afterInstall())
            assertTrue(gate.installed.isEmpty())
        }

    @Test
    fun `an ignored version is not offered at start any more`() =
        scope.runTest {
            val updates = repository()
            updates.checkAtStart()
            updates.ignore()
            assertEquals("1.2.0", preferences.updates.first { it.ignoredVersion != null }.ignoredVersion)
            preferences.setLastUpdateCheck(now.minusSeconds(2 * 86_400))
            assertFalse(repository().checkAtStart())
            // From the settings it shows, marked.
            val manual = repository()
            manual.check()
            assertTrue(
                (manual.state.first { it.phase is UpdatePhase.Available }.phase as UpdatePhase.Available).ignored,
            )
        }

    @Test
    fun `a development build says so, and asks nothing of GitHub`() =
        scope.runTest {
            val updates = repository(version = "0.1.0-dev")
            assertFalse(updates.checkAtStart())
            updates.check()
            updates.state.first { it.phase == UpdatePhase.Development }
            assertEquals(UpdatePhase.Development, updates.state.value.phase)
            assertEquals(0, server.requestCount)
        }
}
