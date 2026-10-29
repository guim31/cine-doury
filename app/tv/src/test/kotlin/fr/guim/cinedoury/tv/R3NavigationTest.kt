package fr.guim.cinedoury.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.DpRect
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.designsystem.component.Featured
import fr.guim.cinedoury.core.designsystem.component.FeaturedBanner
import fr.guim.cinedoury.core.designsystem.component.PrimaryButton
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.model.SubtitleBackground
import fr.guim.cinedoury.core.model.SubtitlePosition
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.library.LibraryActions
import fr.guim.cinedoury.feature.library.LibraryNavigation
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import fr.guim.cinedoury.feature.player.PlayerActions
import fr.guim.cinedoury.feature.player.PlayerPanel
import fr.guim.cinedoury.feature.player.PlayerScreen
import fr.guim.cinedoury.feature.player.playerPanelLine
import fr.guim.cinedoury.feature.search.KeyboardTags
import fr.guim.cinedoury.feature.search.PreviewVoice
import fr.guim.cinedoury.feature.search.SearchActions
import fr.guim.cinedoury.feature.search.SearchNavigation
import fr.guim.cinedoury.feature.search.SearchScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The returns of the essai réel 3 (`SPEC.md` § 4.21), with the D-pad alone: → from the open rail
 * goes back to the content on every screen of the first level; the bar of the subtitles reaches its
 * three settings; the banner of the home keeps its buttons in place from one title to the next; the
 * size of the interface sets the width the screen is laid out on.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R3NavigationTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun focusable(tag: String) =
        compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    private fun press(
        key: Key,
        times: Int = 1,
    ) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun assertInRail() = compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertExists()

    private fun assertRailClosed() =
        compose
            .onNode(
                isFocused() and hasAnyAncestor(hasTestTag("rail")),
            ).assertDoesNotExist()

    private object Navigation :
        HomeNavigation,
        LibraryNavigation,
        SearchNavigation {
        override fun navigate(target: RailTarget) = Unit

        override fun open(item: MediaItem) = Unit

        override fun play(item: MediaItem) = Unit

        override fun play(request: PlaybackRequest) = Unit

        override fun customize() = Unit

        override fun quit() = Unit

        override fun sessionEnded() = Unit
    }

    @Test
    fun `right from the open rail goes back to the card left on the home`() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                HomeScreen(state = J2Samples.home, sources = J2Samples.noImages, navigation = Navigation, onRetry = {})
            }
        }
        focusable(HomePreview.PLAY).assertIsFocused()
        press(Key.DirectionDown, times = 2)
        focusable(HomePreview.card("next-up", 0)).assertIsFocused()
        press(Key.DirectionLeft)
        assertInRail()
        // Down the rail first: the focus comes back to the card all the same.
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        assertRailClosed()
        focusable(HomePreview.card("next-up", 0)).assertIsFocused()
        // From the top of the home too: back to « Lire ».
        compose.pressBack()
        focusable(HomePreview.PLAY).assertIsFocused()
        press(Key.DirectionLeft)
        assertInRail()
        press(Key.DirectionRight)
        assertRailClosed()
        focusable(HomePreview.PLAY).assertIsFocused()
    }

    private object NoLibraryActions : LibraryActions {
        override fun selectTab(tab: LibraryTab) = Unit

        override fun setView(view: LibraryView) = Unit

        override fun sortBy(field: SortField) = Unit

        override fun setFilters(filters: ItemFilters) = Unit

        override fun clearFilters() = Unit

        override fun toggleGroupCollections() = Unit

        override fun openGenre(genre: String) = Unit

        override fun onVisible(index: Int) = Unit

        override fun onFocused(index: Int) = Unit

        override fun jumpTo(letter: String) = Unit

        override fun retry() = Unit
    }

    @Test
    fun `right from the open rail goes back to the item left in a library`() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                LibraryScreen(
                    state = J2Samples.library(LibraryState(position = 1)),
                    sources = J2Samples.noImages,
                    actions = NoLibraryActions,
                    navigation = Navigation,
                )
            }
        }
        focusable(LibraryPreview.item(1)).assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        assertInRail()
        press(Key.DirectionRight)
        assertRailClosed()
        focusable(LibraryPreview.item(0)).assertIsFocused()
    }

    private object NoSearchActions : SearchActions {
        override fun type(text: String) = Unit

        override fun erase() = Unit

        override fun clear() = Unit

        override fun setQuery(text: String) = Unit

        override fun chooseGenre(genre: String) = Unit

        override fun opened() = Unit

        override fun clearRecent() = Unit

        override fun retry() = Unit

        override fun ask(candidate: RequestCandidate) = Unit

        override fun toggleSeason(number: Int) = Unit

        override fun confirmRequest() = Unit

        override fun dismissRequest() = Unit
    }

    @Test
    fun `right from the open rail goes back to the key left in the search`() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                SearchScreen(
                    state = J5Samples.idle,
                    sources = J2Samples.noImages,
                    actions = NoSearchActions,
                    navigation = Navigation,
                    voice = PreviewVoice(),
                )
            }
        }
        focusable(KeyboardTags.key("a")).assertIsFocused()
        press(Key.DirectionDown)
        focusable(KeyboardTags.key("h")).assertIsFocused()
        press(Key.DirectionLeft)
        assertInRail()
        press(Key.DirectionRight)
        assertRailClosed()
        focusable(KeyboardTags.key("h")).assertIsFocused()
    }

    private class SettingsRecorder : PlayerActions by J4Samples.noActions {
        var settings by mutableStateOf(J4Samples.resizing.settings)

        override fun updateSettings(change: (PlayerSettings) -> PlayerSettings) {
            settings = change(settings)
        }
    }

    @Test
    fun `the bar of the subtitles reaches and changes each of its three settings`() {
        val actions = SettingsRecorder()
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                PlayerScreen(
                    state = J4Samples.resizing.copy(settings = actions.settings),
                    actions = actions,
                    sources = J2Samples.noImages,
                    onBack = {},
                    previewPanel = PlayerPanel.SubtitleSize,
                )
            }
        }
        focusable(playerPanelLine(0, 0)).assertIsFocused()
        // ↑ and ↓ change the size, in place.
        press(Key.DirectionUp)
        assertEquals(1.6f, actions.settings.subtitles.scale)
        press(Key.DirectionDown, times = 2)
        assertEquals(1.2f, actions.settings.subtitles.scale)
        focusable(playerPanelLine(0, 0)).assertIsFocused()
        // → to the position, ↑ raises the subtitles.
        press(Key.DirectionRight)
        focusable(playerPanelLine(0, 1)).assertIsFocused()
        press(Key.DirectionUp)
        assertEquals(SubtitlePosition.Raised, actions.settings.subtitles.position)
        // → to the background, ↑ gives the subtitles a shade, ↓ takes it away.
        press(Key.DirectionRight)
        focusable(playerPanelLine(0, 2)).assertIsFocused()
        press(Key.DirectionUp, times = 2)
        assertEquals(SubtitleBackground.Band, actions.settings.subtitles.background)
        press(Key.DirectionDown)
        assertEquals(SubtitleBackground.Shaded, actions.settings.subtitles.background)
        // ← back to the size; the other two kept their values.
        press(Key.DirectionLeft, times = 2)
        focusable(playerPanelLine(0, 0)).assertIsFocused()
        assertEquals(1.2f, actions.settings.subtitles.scale)
        assertEquals(SubtitlePosition.Raised, actions.settings.subtitles.position)
    }

    @Test
    fun `the banner of the home stays in place while the focus moves along and between rows`() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                HomeScreen(state = J2Samples.home, sources = J2Samples.noImages, navigation = Navigation, onRetry = {})
            }
        }
        focusable(HomePreview.PLAY).assertIsFocused()

        // Vertical positions: « Lire » becomes « Reprendre » on a title started, a little wider.
        fun banner(): List<Float> =
            listOf(HomePreview.BANNER, HomePreview.PLAY).flatMap { tag ->
                compose
                    .onNodeWithTag(tag)
                    .getBoundsInRoot()
                    .toRect()
                    .let { listOf(it.top, it.bottom) }
            }
        press(Key.DirectionDown)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        val first = banner()
        // Along the first row, then down two rows and back up: the banner never moves.
        press(Key.DirectionRight, times = 2)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(first, banner())
        press(Key.DirectionDown, times = 2)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(first, banner())
        press(Key.DirectionUp)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(first, banner())
        // And where it stood with its buttons focused.
        press(Key.DirectionUp, times = 2)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag(HomePreview.BANNER))).assertExists()
        assertEquals(first, banner())
    }

    @Test
    fun `the buttons of the banner stay in place from one title to the next`() {
        val long =
            Featured(
                key = "long",
                title = "Le gardien du phare et la tempête du siècle, version longue",
                metadata = "2001 · 1 h 52 · Drame · Tous publics",
            )
        val short = Featured(key = "short", title = "Soleil")
        var featured by mutableStateOf(long)
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                Box(Modifier.fillMaxSize()) {
                    FeaturedBanner(featured = featured) {
                        PrimaryButton(text = "Lire", onClick = {}, modifier = Modifier.testTag("play"))
                    }
                }
            }
        }

        fun play(): Rect = compose.onNodeWithTag("play").getBoundsInRoot().toRect()
        val before = play()
        compose.runOnIdle { featured = short }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(before, play())
        compose.runOnIdle { featured = long.copy(key = "long-again", metadata = null) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(before, play())
    }

    @Test
    fun `the size of the interface sets the width the screen is laid out on`() {
        val widths = mutableMapOf<LayoutSize, Float>()
        var size by mutableStateOf(LayoutSize.Default)
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark, layoutSize = size) {
                val width = LocalWindowInfo.current.containerSize.width
                if (width > 0) widths[size] = width / LocalDensity.current.density
            }
        }
        for (each in LayoutSize.entries) {
            compose.runOnIdle { size = each }
            compose.waitForIdle()
        }
        // 1920 px wide: 1 440 dp in Très compacte (the default since essai réel 4), 1 200 in Compacte,
        // 1 080 in Normale.
        assertEquals(LayoutSize.VeryCompact, LayoutSize.Default)
        assertEquals(1440f, widths.getValue(LayoutSize.VeryCompact), 1f)
        assertEquals(1200f, widths.getValue(LayoutSize.Compact), 1f)
        assertEquals(1080f, widths.getValue(LayoutSize.Normal), 1f)
    }
}

private fun DpRect.toRect() = Rect(left.value, top.value, right.value, bottom.value)

private const val SETTLE_MILLIS = 1_000L
