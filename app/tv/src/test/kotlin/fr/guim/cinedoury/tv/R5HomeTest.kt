package fr.guim.cinedoury.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mosaic « Ce soir » of the essai réel 5 (`SPEC.md` § 4.23), in each size of the interface: the
 * large tile focused first, a row and a half under it, the mosaic scrolled away by index when the
 * focus goes down, the titles of the rows still, and OK playing or opening. In 1080p here, in 4K below.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5Home1080pTest(
    size: LayoutSize,
) : R5HomeChecks(size) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_4K_SHIELD)
class R5Home4kTest(
    size: LayoutSize,
) : R5HomeChecks(size) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

abstract class R5HomeChecks(
    private val size: LayoutSize,
) {
    @get:Rule
    val compose = createTvComposeRule()

    private val opened = mutableListOf<MediaItem>()
    private val played = mutableListOf<MediaItem>()

    private val navigation =
        object : HomeNavigation {
            override fun navigate(target: RailTarget) = Unit

            override fun open(item: MediaItem) {
                opened += item
            }

            override fun play(item: MediaItem) {
                played += item
            }

            override fun play(request: PlaybackRequest) {
                played += request.item
            }

            override fun customize() = Unit

            override fun quit() = Unit

            override fun sessionEnded() = Unit
        }

    private fun home() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark, layoutSize = size) {
                HomeScreen(
                    state = R5Samples.tonight,
                    sources = J2Samples.noImages,
                    navigation = navigation,
                    onRetry = {},
                )
            }
        }
        focusable(HomePreview.tonight(0)).assertIsFocused()
    }

    private fun focusable(tag: String) =
        compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().top

    @Test
    fun fiveTilesTheLargeOneFocusedFirst() {
        home()
        (0 until TILES).forEach {
            assertTrue(
                compose.onAllNodesWithTag(HomePreview.tonight(it)).fetchSemanticsNodes().isNotEmpty(),
                "tile $it",
            )
        }
        // « Reprendre » keeps only what the mosaic has no room for; « À suivre » is gone.
        assertTrue(compose.onAllNodesWithTag("row-next-up").fetchSemanticsNodes().isEmpty(), "« À suivre » left")
    }

    @Test
    fun aRowAndAHalfUnderTheMosaic() {
        home()
        val screen = compose.onNodeWithTag("home").getBoundsInRoot().bottom
        val row = compose.onNodeWithTag("row-resume").getUnclippedBoundsInRoot()
        // Against the height of the second row itself: posters, under a row of thumbnails.
        val second = compose.onNodeWithTag("row-latest:films").getUnclippedBoundsInRoot()
        val shown = (screen - top(HomePreview.rowTitle("latest:films"))) / (second.bottom - second.top)
        // In « Normale » 1080p, the lowest mosaic leaves the title of the second row and the top of its
        // cards, as the banner of the essai réel 4 did.
        val expected = if (size == LayoutSize.Normal) PEEK..HALF_ROW.endInclusive else HALF_ROW
        assertTrue(shown in expected, "$size: ${shown * 100} % of the second row shows")
        // The mosaic ends above the first row.
        assertTrue(compose.onNodeWithTag(HomePreview.TONIGHT).getBoundsInRoot().bottom <= row.top, "$size")
    }

    @Test
    fun theMosaicScrollsAwayByIndexAndComesBack() {
        home()
        // → along the tiles of the top line, then ↓ into the rows: the mosaic leaves the screen.
        press(Key.DirectionRight)
        focusable(HomePreview.tonight(1)).assertIsFocused()
        press(Key.DirectionDown, times = 2)
        focusable(HomePreview.card("resume", 0)).assertIsFocused()
        val home = compose.onNodeWithTag("home").getBoundsInRoot().top
        val title = top(HomePreview.rowTitle("resume"))
        val mosaic = compose.onAllNodesWithTag(HomePreview.TONIGHT).fetchSemanticsNodes()
        val gone =
            mosaic.isEmpty() ||
                compose.onNodeWithTag(HomePreview.TONIGHT).getUnclippedBoundsInRoot().bottom <= home + EDGE
        assertTrue(gone, "$size: the mosaic is still on screen")
        // The next row comes where the first one stood; the title stays along the row.
        press(Key.DirectionDown)
        assertEquals(title, top(HomePreview.rowTitle("latest:films")), "$size")
        press(Key.DirectionRight)
        assertEquals(title, top(HomePreview.rowTitle("latest:films")), "$size, along the row")
        // Back up: the mosaic comes back, a tile focused.
        press(Key.DirectionUp, times = 2)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag(HomePreview.TONIGHT))).assertExists()
        assertTrue(compose.onNodeWithTag(HomePreview.TONIGHT).getBoundsInRoot().top >= home, "$size, mosaic back")
    }

    @Test
    fun okPlaysWhatIsInProgressAndOpensTheRest() {
        home()
        press(Key.DirectionCenter)
        assertEquals(listOf(J2Samples.resumeEpisode.id), played.map { it.id }, "the large tile resumes")
        // The bottom left small tile: a film added yesterday, its details.
        press(Key.DirectionRight)
        press(Key.DirectionDown)
        focusable(HomePreview.tonight(3)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, opened.size, "an addition opens its details")
        assertEquals(1, played.size)
    }

    private companion object {
        const val TILES = 5
        val HALF_ROW = 0.4f..0.6f
        const val PEEK = 0.2f
        val EDGE =
            androidx.compose.ui.unit
                .Dp(0.5f)
        const val SETTLE_MILLIS = 1_000L
    }
}
