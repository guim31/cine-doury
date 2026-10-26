package fr.guim.cinedoury.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.guim.cinedoury.benchmark.Journeys.browseHome
import fr.guim.cinedoury.benchmark.Journeys.libraryRoundTrip
import fr.guim.cinedoury.benchmark.Journeys.scrollGrid
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Baseline Profile and the Startup Profile of the app (`SPEC.md` § 4.16): what a start to the
 * home and the journeys of every day run, compiled ahead of time once the APK is installed.
 * `./gradlew :app:tv:generateReleaseBaselineProfile` on the emulator of the CI, with the test
 * Jellyfin; the profiles are versioned in `app/tv/src/release/generated/baselineProfiles/`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun signIn() = Remote().launchSignedIn()

    /** A cold start to the home: the Startup Profile, which R8 lays out first in the dex. */
    @Test
    fun startup() =
        rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            Remote(device).waitHome()
        }

    /** The home, a library, a details screen and a fast scroll of the grid. */
    @Test
    fun journeys() =
        rule.collect(packageName = PACKAGE) {
            pressHome()
            startActivityAndWait()
            val remote = Remote(device)
            remote.waitHome()
            remote.browseHome()
            remote.libraryRoundTrip()
            remote.openLibrary(FILMS)
            remote.scrollGrid()
        }
}
