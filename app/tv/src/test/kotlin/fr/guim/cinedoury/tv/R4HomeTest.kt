package fr.guim.cinedoury.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
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
 * The home of the essai réel 4 (`SPEC.md` § 4.22), in each size of the interface: the banner leaves
 * room for a second row under the first (B), and the title of a row never moves with the focus (C).
 * In 1080p here, in 4K below.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R4Home1080pTest(
    size: LayoutSize,
) : R4HomeChecks(size) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_4K_SHIELD)
class R4Home4kTest(
    size: LayoutSize,
) : R4HomeChecks(size) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun sizes(): List<Array<Any>> = LayoutSize.entries.map { arrayOf(it) }
    }
}

abstract class R4HomeChecks(
    private val size: LayoutSize,
) {
    @get:Rule
    val compose = createTvComposeRule()

    private object Navigation : HomeNavigation {
        override fun navigate(target: RailTarget) = Unit

        override fun open(item: MediaItem) = Unit

        override fun play(item: MediaItem) = Unit

        override fun customize() = Unit

        override fun quit() = Unit

        override fun sessionEnded() = Unit
    }

    /** Rows of posters only: the tallest, the hardest case for the second row to show. */
    private val posters =
        J2Samples.home.copy(
            rows =
                J2Samples.homeRows.filter {
                    it.key != "resume" &&
                        it.key != "next-up"
                },
        )

    private fun home() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark, layoutSize = size) {
                HomeScreen(state = posters, sources = J2Samples.noImages, navigation = Navigation, onRetry = {})
            }
        }
        focusable(HomePreview.PLAY).assertIsFocused()
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

    /** The top of the cards of the second row is on the screen, a little more than a strip of them. */
    private fun assertSecondRowShows(what: String) {
        val screen = compose.onRoot().getBoundsInRoot().bottom
        val second = top(HomePreview.card("latest:series", 0))
        assertTrue(
            screen - second >= PEEK,
            "$what, $size: the second row starts at $second, the screen ends at $screen",
        )
    }

    @Test
    fun aSecondRowShowsUnderTheFirst() {
        home()
        assertSecondRowShows("the banner focused")
        press(Key.DirectionDown)
        focusable(HomePreview.card("latest:films", 0)).assertIsFocused()
        assertSecondRowShows("the first row focused")
    }

    @Test
    fun theTitleOfARowStaysInPlaceWhileTheFocusMoves() {
        home()
        press(Key.DirectionDown)
        val title = top(HomePreview.rowTitle("latest:films"))
        // Along the row: the focused card grows, the title stays.
        press(Key.DirectionRight)
        assertEquals(title, top(HomePreview.rowTitle("latest:films")), "along the row, $size")
        press(Key.DirectionRight, times = 2)
        assertEquals(title, top(HomePreview.rowTitle("latest:films")), "further along the row, $size")
        // To the next row: its title comes where the first one stood, and stays there along it.
        press(Key.DirectionDown)
        focusable(HomePreview.card("latest:series", 0)).assertIsFocused()
        assertEquals(title, top(HomePreview.rowTitle("latest:series")), "the next row, $size")
        press(Key.DirectionRight)
        assertEquals(title, top(HomePreview.rowTitle("latest:series")), "along the next row, $size")
        // And back up.
        press(Key.DirectionUp)
        assertEquals(title, top(HomePreview.rowTitle("latest:films")), "back up, $size")
    }

    private companion object {
        val PEEK = 16.dp
        const val SETTLE_MILLIS = 1_000L
    }
}
