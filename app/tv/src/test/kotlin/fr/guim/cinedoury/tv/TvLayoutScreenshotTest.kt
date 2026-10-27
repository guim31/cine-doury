package fr.guim.cinedoury.tv

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A Shield TV in 4K lays its apps out on 1920 × 1080 dp at xhdpi (3840 × 2160 pixels): the app must
 * still show the screen of the design, 960 × 540 dp (`SPEC.md` § 4.16). The library grid of J2,
 * shot there, is the reference screenshot of 1080p at twice the size: once scaled down, the same
 * cards at the same places, pixel for pixel but for the edges of the letters.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_4K_SHIELD)
class TvLayoutScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun aShieldIn4kShowsTheScreenOf1080p() {
        compose.setContent {
            CineTheme {
                CompositionLocalProvider(LocalPreviewFocus provides LibraryPreview.item(2)) {
                    LibraryScreen(
                        state = J2Samples.library(),
                        sources = J2Samples.noImages,
                        actions = NoLibraryActions,
                        navigation = NoLibraryNavigation,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/150-bibliotheque-grille-4k-sombre.png")

        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(WIDTH_4K, shot.width, "a 4K screen")
        val scaled = Bitmap.createScaledBitmap(shot, WIDTH_1080P, HEIGHT_1080P, true)
        val reference = ImageIO.read(File("src/test/screenshots/50-bibliotheque-grille-sombre.png"))
        assertEquals(WIDTH_1080P, reference.width)

        var differing = 0
        for (y in 0 until HEIGHT_1080P) {
            for (x in 0 until WIDTH_1080P) {
                if (distance(scaled.getPixel(x, y), reference.getRGB(x, y)) > TOLERANCE) differing++
            }
        }
        val share = differing.toFloat() / (WIDTH_1080P * HEIGHT_1080P)
        assertTrue(share < MAX_DIFFERING, "${share * PERCENT} % of the pixels differ from the 1080p screenshot")
    }

    /** The largest difference between the channels of two colours. */
    private fun distance(
        a: Int,
        b: Int,
    ): Int =
        CHANNELS.maxOf { shift ->
            abs((a shr shift and BYTE) - (b shr shift and BYTE))
        }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val WIDTH_4K = 3840
        const val WIDTH_1080P = 1920
        const val HEIGHT_1080P = 1080
        const val BYTE = 0xFF
        val CHANNELS = listOf(16, 8, 0)

        /** Scaling smooths the edges of letters and shapes: a little difference there. */
        const val TOLERANCE = 48

        /** The edges of the text and the shapes, never a card out of place (one is 3 % of the screen). */
        const val MAX_DIFFERING = 0.01f
        const val PERCENT = 100
    }
}

/** A Shield TV in 4K, as `am get-config` gives it: 1920 × 1080 dp at xhdpi, 3840 × 2160 pixels. */
internal const val TV_4K_SHIELD = "fr-rFR-w1920dp-h1080dp-land-television-xhdpi-notouch"
