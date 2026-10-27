package fr.guim.cinedoury.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.guim.cinedoury.benchmark.Journeys.browseHome
import fr.guim.cinedoury.benchmark.Journeys.libraryRoundTrip
import fr.guim.cinedoury.benchmark.Journeys.scrollGrid
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four measures of `SPEC.md` § 4.16, on the emulator of the CI with the test Jellyfin. The
 * emulator is not a Shield: its numbers compare the app with itself, before and after a change.
 *
 * The argument `compilation` says how the app is compiled: `profile` (default) the Baseline Profile
 * of the APK, as installed from the CI on a device; `none` nothing (interpreted then JIT); `ignore`
 * as found, the only choice for the debug APK, which Android does not compile.
 */
@OptIn(ExperimentalMacrobenchmarkApi::class)
@RunWith(AndroidJUnit4::class)
class NavigationBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val compilation: CompilationMode =
        when (InstrumentationRegistry.getArguments().getString("compilation")) {
            "none" -> CompilationMode.None()
            "ignore" -> CompilationMode.Ignore()
            else -> CompilationMode.Partial(BaselineProfileMode.UseIfAvailable)
        }

    @Before
    fun signIn() = Remote().launchSignedIn()

    /** A cold start, to the home with its rows (« fully drawn »). */
    @Test
    fun startToHome() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            Remote(device).waitHome()
        }

    /** The rows of the home with the remote: down, along a row and back, up. */
    @Test
    fun browseHome() = frames { browseHome() }

    /** Home → rail → library → grid → details → back to the home. */
    @Test
    fun libraryRoundTrip() = frames { libraryRoundTrip() }

    /** The grid of « Films » with the key held down, then up. */
    @Test
    fun scrollGrid() =
        frames(setup = { openLibrary(FILMS) }) { scrollGrid() }

    @OptIn(ExperimentalMetricApi::class)
    private fun frames(
        setup: Remote.() -> Unit = {},
        journey: Remote.() -> Unit,
    ) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        // Not COLD: Macrobenchmark would kill the app after the setup, for a start to measure. The
        // setup starts it afresh itself.
        startupMode = null,
        iterations = FRAME_ITERATIONS,
        setupBlock = { start().setup() },
    ) {
        Remote(device).journey()
    }

    private fun MacrobenchmarkScope.start(): Remote {
        killProcess()
        startActivityAndWait()
        return Remote(device).apply { waitHome() }
    }

    private companion object {
        const val STARTUP_ITERATIONS = 5
        const val FRAME_ITERATIONS = 3
    }
}
