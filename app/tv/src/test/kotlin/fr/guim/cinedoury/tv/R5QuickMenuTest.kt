package fr.guim.cinedoury.tv

import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.designsystem.component.QUICK_MENU
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.QuickOption
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import android.view.KeyEvent as NativeKeyEvent

/** OK held on a card of the screen under test (`SPEC.md` § 4.23): the repeats of the remote, then the release. */
internal fun TvComposeRule.holdOk() {
    val down = { repeat: Int ->
        NativeKeyEvent(0L, 0L, NativeKeyEvent.ACTION_DOWN, NativeKeyEvent.KEYCODE_DPAD_CENTER, repeat)
    }
    onRoot().performKeyPress(KeyEvent(down(0)))
    onRoot().performKeyPress(KeyEvent(down(1)))
    onRoot().performKeyPress(KeyEvent(down(2)))
    waitForIdle()
    onRoot().performKeyPress(
        KeyEvent(NativeKeyEvent(0L, 0L, NativeKeyEvent.ACTION_UP, NativeKeyEvent.KEYCODE_DPAD_CENTER, 0)),
    )
    waitForIdle()
}

/** The tag of the line of [option] in the menu of a card. */
internal fun quickLine(option: QuickOption) = "quick-${option.name}"

/**
 * Essai réel 5, C: the menu of a card, on the home (`SPEC.md` § 4.23): opened by OK held and not by a
 * short press, its options for the item, the focus kept in it, then given back to the card.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5QuickMenuTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val opened = mutableListOf<MediaItem>()
    private val played = mutableListOf<PlaybackRequest>()

    private val navigation =
        object : HomeNavigation {
            override fun navigate(target: RailTarget) = Unit

            override fun open(item: MediaItem) {
                opened += item
            }

            override fun play(item: MediaItem) {
                played += PlaybackRequest(item)
            }

            override fun play(request: PlaybackRequest) {
                played += request
            }

            override fun customize() = Unit

            override fun quit() = Unit

            override fun sessionEnded() = Unit
        }

    private fun home() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                HomeScreen(
                    state = J2Samples.home,
                    sources = J2Samples.noImages,
                    navigation = navigation,
                    onRetry = {},
                )
            }
        }
        focusable(HomePreview.PLAY).assertIsFocused()
        // « Reprendre »: « Mortes-eaux », an episode started.
        press(Key.DirectionDown)
        focusable(RESUME).assertIsFocused()
    }

    private fun focusable(tag: String): SemanticsNodeInteraction =
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

    private fun menuShown() = compose.onAllNodes(hasTestTag(QUICK_MENU)).fetchSemanticsNodes().isNotEmpty()

    private fun shown(option: QuickOption) =
        compose.onAllNodes(hasTestTag(quickLine(option))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aShortPressOpensTheCardAndOkHeldOpensTheMenu() {
        home()
        press(Key.DirectionCenter)
        assertEquals(listOf(J2Samples.resumeEpisode.id), opened.map { it.id }, "a short press opens the card")
        assertTrue(!menuShown(), "no menu on a short press")
        compose.holdOk()
        assertTrue(menuShown(), "OK held opens the menu")
        assertEquals(1, opened.size, "OK held does not open the card as well")
    }

    @Test
    fun theOptionsOfAnEpisodeInProgressOnTheHome() {
        home()
        compose.holdOk()
        val expected =
            listOf(
                QuickOption.Resume,
                QuickOption.PlayFromStart,
                QuickOption.OpenDetails,
                QuickOption.GoToSeries,
                QuickOption.MarkWatched,
                QuickOption.RemoveFromContinue,
                QuickOption.AddFavorite,
            )
        expected.forEach { assertTrue(shown(it), "$it in the menu") }
        // No season known, no management, no selection on the home.
        listOf(QuickOption.Play, QuickOption.GoToSeason, QuickOption.Select).forEach {
            assertTrue(!shown(it), "$it not in the menu")
        }
        // The first option has the focus; the release of OK chose nothing.
        focusable(quickLine(QuickOption.Resume)).assertIsFocused()
        assertTrue(played.isEmpty())
    }

    @Test
    fun theFocusStaysInTheMenuAndBackGivesItBackToTheCard() {
        home()
        compose.holdOk()
        press(Key.DirectionLeft, times = 3)
        press(Key.DirectionUp, times = 2)
        press(Key.DirectionDown, times = 12)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag(QUICK_MENU))).assertExists()
        compose.pressBack()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertTrue(!menuShown(), "« Retour » closes the menu")
        focusable(RESUME).assertIsFocused()
    }

    @Test
    fun anOptionClosesTheMenuGivesTheFocusBackAndLeads() {
        home()
        compose.holdOk()
        // « Lire depuis le début », the second line.
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertTrue(!menuShown())
        assertEquals(listOf(false), played.map { it.resume }, "played from the start")
        focusable(RESUME).assertIsFocused()
    }

    private companion object {
        val RESUME = HomePreview.card("resume", 0)
        const val SETTLE_MILLIS = 500L
    }
}
