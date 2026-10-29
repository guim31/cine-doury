package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.library.LibraryState
import fr.guim.cinedoury.core.data.library.LibraryTab
import fr.guim.cinedoury.core.data.library.LibraryView
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ItemFilters
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.model.SortField
import fr.guim.cinedoury.core.ui.Events
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomePreview
import fr.guim.cinedoury.feature.home.HomeScreen
import fr.guim.cinedoury.feature.library.Jump
import fr.guim.cinedoury.feature.library.LibraryActions
import fr.guim.cinedoury.feature.library.LibraryNavigation
import fr.guim.cinedoury.feature.library.LibraryPreview
import fr.guim.cinedoury.feature.library.LibraryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The home and the libraries with the D-pad alone (`UI.md` § 2 and § 3): the rail, the rows that
 * remember their card, « Retour » on the home, the grid that opens where it was left, the A–Z jump.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J2NavigationTest {
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

    /** [layoutSize]: the columns of a grid depend on it (« Taille de l'interface », essai réel 3). */
    private fun show(
        layoutSize: LayoutSize = LayoutSize.Default,
        content: @Composable () -> Unit,
    ) {
        compose.setTvContent { CineTheme(ThemeVariant.Dark, layoutSize = layoutSize, content = content) }
        compose.waitForIdle()
    }

    private class Recorder :
        HomeNavigation,
        LibraryNavigation {
        val targets = mutableListOf<RailTarget>()
        val opened = mutableListOf<MediaItem>()
        var quit = false

        override fun navigate(target: RailTarget) {
            targets += target
        }

        override fun open(item: MediaItem) {
            opened += item
        }

        override fun play(item: MediaItem) = Unit

        override fun play(request: PlaybackRequest) = Unit

        override fun customize() = Unit

        override fun quit() {
            quit = true
        }

        override fun sessionEnded() = Unit
    }

    private val recorder = Recorder()

    private fun home() =
        show {
            HomeScreen(state = J2Samples.home, sources = J2Samples.noImages, navigation = recorder, onRetry = {})
        }

    @Test
    fun `the home opens on Play, and each row gives back the card it had`() {
        home()
        focusable(HomePreview.PLAY).assertIsFocused()
        press(Key.DirectionDown)
        focusable(HomePreview.card("resume", 0)).assertIsFocused()
        press(Key.DirectionRight, times = 2)
        focusable(HomePreview.card("resume", 2)).assertIsFocused()
        press(Key.DirectionDown)
        focusable(HomePreview.card("next-up", 0)).assertIsFocused()
        press(Key.DirectionUp)
        focusable(HomePreview.card("resume", 2)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(J2Samples.homeRows[0].items[2], recorder.opened.single())
    }

    @Test
    fun `the rail opens on the left and leads to a library`() {
        home()
        press(Key.DirectionDown)
        press(Key.DirectionLeft)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertExists()
        // The rail opens on « Accueil »; then « Séries », « Films ».
        press(Key.DirectionDown, times = 2)
        press(Key.DirectionCenter)
        assertEquals(RailTarget.Library(J2Samples.films.id), recorder.targets.last())
    }

    @Test
    fun `Back goes to the top, then into the rail, then quits without asking`() {
        home()
        press(Key.DirectionDown, times = 2)
        focusable(HomePreview.card("next-up", 0)).assertIsFocused()
        compose.pressBack()
        focusable(HomePreview.PLAY).assertIsFocused()
        assertEquals(false, recorder.quit)
        compose.pressBack()
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("rail"))).assertExists()
        assertEquals(false, recorder.quit)
        // Essai réel 4: no « Quitter Ciné Doury ? » any more.
        compose.pressBack()
        assertEquals(true, recorder.quit)
        compose.onNode(hasTestTag("quit-dialog")).assertDoesNotExist()
    }

    private class LibraryRecorder : LibraryActions {
        val letters = mutableListOf<String>()
        val focused = mutableListOf<Int>()

        override fun selectTab(tab: LibraryTab) = Unit

        override fun setView(view: LibraryView) = Unit

        override fun sortBy(field: SortField) = Unit

        override fun setFilters(filters: ItemFilters) = Unit

        override fun clearFilters() = Unit

        override fun toggleGroupCollections() = Unit

        override fun openGenre(genre: String) = Unit

        override fun onVisible(index: Int) = Unit

        override fun onFocused(index: Int) {
            focused += index
        }

        override fun jumpTo(letter: String) {
            letters += letter
        }

        override fun retry() = Unit
    }

    @Test
    fun `a library opens on the item left last time, and its grid goes item by item`() {
        val actions = LibraryRecorder()
        // Five columns in « Normale ».
        show(LayoutSize.Normal) {
            LibraryScreen(
                state = J2Samples.library(LibraryState(position = 5)),
                sources = J2Samples.noImages,
                actions = actions,
                navigation = recorder,
            )
        }
        // Five columns: 5 is the first of the second row.
        focusable(LibraryPreview.item(5)).assertIsFocused()
        press(Key.DirectionRight)
        focusable(LibraryPreview.item(6)).assertIsFocused()
        assertEquals(6, actions.focused.last())
        press(Key.DirectionUp)
        focusable(LibraryPreview.item(1)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(J2Samples.movies[1], recorder.opened.single())
    }

    @Test
    fun `the A to Z bar jumps to a letter`() {
        val actions = LibraryRecorder()
        val jumps = Events<Jump>()
        // Six columns, in « Compacte » (seven in « Très compacte », the default since essai réel 4).
        show(LayoutSize.Compact) {
            LibraryScreen(
                state = J2Samples.library(),
                sources = J2Samples.noImages,
                actions = actions,
                navigation = recorder,
                jumps = jumps,
            )
        }
        focusable(LibraryPreview.item(0)).assertIsFocused()
        // Far right of the first row, then into the bar.
        press(Key.DirectionRight, times = 6)
        compose.onNode(isFocused() and hasAnyAncestor(hasTestTag("alphabet"))).assertExists()
        press(Key.DirectionCenter)
        assertEquals(1, actions.letters.size)
        // The view model answers with the index of the first title at that letter.
        compose.runOnIdle { jumps.send(Jump(15)) }
        compose.waitForIdle()
        focusable(LibraryPreview.item(15)).assertIsFocused()
    }
}
