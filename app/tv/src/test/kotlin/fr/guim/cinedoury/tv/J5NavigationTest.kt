package fr.guim.cinedoury.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.RequestCandidate
import fr.guim.cinedoury.core.model.SearchGroup
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.search.KeyboardTags
import fr.guim.cinedoury.feature.search.PreviewVoice
import fr.guim.cinedoury.feature.search.SearchActions
import fr.guim.cinedoury.feature.search.SearchNavigation
import fr.guim.cinedoury.feature.search.SearchPreview
import fr.guim.cinedoury.feature.search.SearchRows
import fr.guim.cinedoury.feature.search.SearchScreen
import fr.guim.cinedoury.feature.search.SearchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The on-screen keyboard of the search with the D-pad alone (`UI.md` § 2): the focus starts on « a »,
 * every key has a neighbour in the four directions, the results are on the right, the rail on the
 * left; the accents are one key away.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J5NavigationTest {
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

    /** A search that types as the view model does, and records what the screen asks. */
    private class Recorder :
        SearchActions,
        SearchNavigation {
        var state by mutableStateOf(J5Samples.idle)
        val opened = mutableListOf<MediaItem>()
        val navigated = mutableListOf<RailTarget>()

        override fun type(text: String) {
            state = state.copy(query = state.query + text)
        }

        override fun erase() {
            state = state.copy(query = state.query.dropLast(1))
        }

        override fun clear() {
            state = state.copy(query = "")
        }

        override fun setQuery(text: String) {
            state = state.copy(query = text)
        }

        override fun chooseGenre(genre: String) {
            state = state.copy(genre = genre)
        }

        override fun opened() = Unit

        override fun clearRecent() = Unit

        override fun retry() = Unit

        override fun ask(candidate: RequestCandidate) = Unit

        override fun toggleSeason(number: Int) = Unit

        override fun confirmRequest() = Unit

        override fun dismissRequest() = Unit

        override fun navigate(target: RailTarget) {
            navigated += target
        }

        override fun open(item: MediaItem) {
            opened += item
        }

        override fun sessionEnded() = Unit
    }

    private fun show(
        recorder: Recorder,
        initial: SearchState = J5Samples.idle,
    ) {
        recorder.state = initial
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                SearchScreen(
                    state = recorder.state,
                    sources = J2Samples.noImages,
                    actions = recorder,
                    navigation = recorder,
                    voice = PreviewVoice(),
                )
            }
        }
    }

    @Test
    fun `the keyboard types with the D-pad, from « a »`() {
        val recorder = Recorder()
        show(recorder)
        focusable(KeyboardTags.key("a")).assertIsFocused()
        // a → b → c, then down to j (the grid has seven keys per row).
        press(Key.DirectionRight, times = 2)
        focusable(KeyboardTags.key("c")).assertIsFocused()
        press(Key.DirectionDown)
        focusable(KeyboardTags.key("j")).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals("jc", recorder.state.query)
        // Down to the last rows: 8 9 0, space and erase; then the page of accents.
        press(Key.DirectionDown, times = 5)
        focusable(KeyboardTags.key("0")).assertIsFocused()
        press(Key.DirectionRight)
        focusable(KeyboardTags.SPACE).assertIsFocused()
        press(Key.DirectionRight)
        focusable(KeyboardTags.ERASE).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("j", recorder.state.query)
        press(Key.DirectionDown)
        focusable(KeyboardTags.CLEAR).assertIsFocused()
        press(Key.DirectionLeft, times = 3)
        focusable(KeyboardTags.PAGE).assertIsFocused()
        press(Key.DirectionCenter)
        // The accents in place of the letters: up to « à », in the corner.
        press(Key.DirectionUp, times = 6)
        focusable(KeyboardTags.key("à")).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("jà", recorder.state.query)
    }

    @Test
    fun `right of the keyboard, the suggestions`() {
        val recorder = Recorder()
        show(recorder)
        focusable(KeyboardTags.key("a")).assertIsFocused()
        press(Key.DirectionRight, times = 7)
        // The first recent search, at the top right.
        focusable(SearchPreview.recent(0)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("Le Phare", recorder.state.query)
    }

    @Test
    fun `a result opens its details, and the keyboard is one key left`() {
        val recorder = Recorder()
        show(recorder, J5Samples.partial)
        press(Key.DirectionRight, times = 7)
        val movies = SearchRows.of(SearchGroup.Movies)
        focusable(SearchPreview.card(movies, 0)).assertIsFocused()
        press(Key.DirectionRight)
        focusable(SearchPreview.card(movies, 1)).assertIsFocused()
        press(Key.DirectionDown)
        focusable(SearchPreview.card(SearchRows.of(SearchGroup.Series), 0)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("Les Marées", recorder.opened.single().title)
        press(Key.DirectionLeft)
        compose.onNode(hasTestTag("keyboard")).assertExists()
    }
}
