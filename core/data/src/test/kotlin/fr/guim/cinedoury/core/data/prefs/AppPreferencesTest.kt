package fr.guim.cinedoury.core.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.guim.cinedoury.core.data.home.ProfileKey
import fr.guim.cinedoury.core.data.library.LibraryView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val store =
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            folder.root.resolve("settings.preferences_pb")
        }
    private val prefs = AppPreferences(store)
    private val alice = ProfileKey("server", "alice")
    private val bob = ProfileKey("server", "bob")

    @Test
    fun appearanceHasItsDefaultsThenKeepsItsChoices() =
        scope.runTest {
            assertEquals(Appearance(ThemeChoice.Dark, reducedMotion = false, TextSize.Normal), prefs.appearance.first())
            prefs.setAppearance { it.copy(theme = ThemeChoice.OledBlack, reducedMotion = true) }
            prefs.setAppearance { it.copy(textSize = TextSize.Larger) }
            assertEquals(
                Appearance(ThemeChoice.OledBlack, reducedMotion = true, TextSize.Larger),
                prefs.appearance.first(),
            )
        }

    @Test
    fun theSizeOfTheInterfaceIsCompactByDefaultAndTheDroppedLargeReadsAsNormal() =
        scope.runTest {
            assertEquals(InterfaceSize.Compact, prefs.appearance.first().interfaceSize)
            prefs.setAppearance { it.copy(interfaceSize = InterfaceSize.VeryCompact) }
            assertEquals(InterfaceSize.VeryCompact, prefs.appearance.first().interfaceSize)
            // « Grande », chosen before 28/10.
            store.edit { it[stringPreferencesKey("interface_size")] = "Large" }
            assertEquals(InterfaceSize.Normal, prefs.appearance.first().interfaceSize)
        }

    @Test
    fun browsingIsKeptPerProfile() =
        scope.runTest {
            prefs.setBrowsing(
                alice,
            ) { it.copy(featuredBanner = false, posterTitles = true, defaultView = LibraryView.List) }
            assertEquals(
                Browsing(
                    featuredBanner = false,
                    posterTitles = true,
                    unwatchedMarks = true,
                    defaultView = LibraryView.List,
                ),
                prefs.browsing(alice).first(),
            )
            assertEquals(Browsing(), prefs.browsing(bob).first())
        }

    @Test
    fun theNameOfTheDeviceFallsBackToTheTvs() =
        scope.runTest {
            assertNull(prefs.deviceName.first())
            prefs.setDeviceName("  Salon  ")
            assertEquals("Salon", prefs.deviceName.first())
            prefs.setDeviceName(" ")
            assertNull(prefs.deviceName.first())
        }

    @Test
    fun networkModeAndUpdates() =
        scope.runTest {
            assertEquals(NetworkMode.Automatic, prefs.networkMode.first())
            prefs.setNetworkMode(NetworkMode.LocalOnly)
            assertEquals(NetworkMode.LocalOnly, prefs.networkMode.first())
            val now = Instant.parse("2026-10-26T10:00:00Z")
            prefs.setLastUpdateCheck(now)
            prefs.setIgnoredVersion("1.2.0")
            assertEquals(UpdateMemory(now, "1.2.0"), prefs.updates.first())
            prefs.setIgnoredVersion(null)
            assertNull(prefs.updates.first().ignoredVersion)
        }
}
