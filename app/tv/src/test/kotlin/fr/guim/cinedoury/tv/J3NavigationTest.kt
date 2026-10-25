package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ItemId
import fr.guim.cinedoury.core.model.ItemKind
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.model.PlaybackRequest
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.feature.details.DetailsActions
import fr.guim.cinedoury.feature.details.DetailsNavigation
import fr.guim.cinedoury.feature.details.DetailsPreview
import fr.guim.cinedoury.feature.details.DetailsScreen
import fr.guim.cinedoury.feature.details.DetailsState
import fr.guim.cinedoury.feature.details.panelLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The details screens with the D-pad alone (`UI.md` § 2, § 3 and § 8): the focus starts on « Lire »
 * or « Reprendre », the round buttons follow on its right, the rows under them, the track panel opens
 * and gives the focus back to its button when it closes.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J3NavigationTest {
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

    /** Records what the screen asks. */
    private class Recorder :
        DetailsActions,
        DetailsNavigation {
        val opened = mutableListOf<MediaItem>()
        val played = mutableListOf<PlaybackRequest>()
        val audio = mutableListOf<Int>()
        val seasons = mutableListOf<ItemId>()
        val watched = mutableListOf<MediaItem>()

        override fun retry() = Unit

        override fun toggleWatched(item: MediaItem) {
            watched += item
        }

        override fun toggleFavorite() = Unit

        override fun selectSeason(season: ItemId) {
            seasons += season
        }

        override fun selectVersion(sourceId: String) = Unit

        override fun selectAudio(index: Int) {
            audio += index
        }

        override fun selectSubtitles(index: Int?) = Unit

        override fun playRequest(fromStart: Boolean): PlaybackRequest =
            PlaybackRequest(
                J3Samples.phare,
                resume = !fromStart,
            )

        override fun open(item: MediaItem) {
            opened += item
        }

        override fun play(request: PlaybackRequest) {
            played += request
        }

        override fun openTrailer(url: String) = Unit

        override fun sessionEnded() = Unit
    }

    private val recorder = Recorder()

    private fun show(state: DetailsState) {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                CompositionLocalProvider(LocalClock provides J3Samples.clock) {
                    show(state, recorder)
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun show(
        state: DetailsState,
        recorder: Recorder,
    ) = DetailsScreen(state = state, sources = J2Samples.noImages, actions = recorder, navigation = recorder)

    @Test
    fun `a film opens on « Reprendre », then the round buttons, then the cast`() {
        show(J3Samples.film)
        focusable(DetailsPreview.PLAY).assertIsFocused()
        compose.onNode(hasText("Reprendre — 1\u00A0h\u00A009 restantes")).assertExists()
        compose.onNode(hasText("se termine à 22\u00A0h\u00A007", substring = true)).assertExists()
        press(Key.DirectionRight)
        focusable(DetailsPreview.RESTART).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(false, recorder.played.single().resume)
        press(Key.DirectionRight, times = 3)
        focusable(DetailsPreview.WATCHED).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(J3Samples.phare.id, recorder.watched.single().id)
        // Down to the cast; OK opens the person.
        press(Key.DirectionDown)
        focusable(DetailsPreview.card("cast", 0)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(ItemKind.Person, recorder.opened.single().kind)
        assertEquals("Camille Martin", recorder.opened.single().title)
    }

    @Test
    fun `the track panel opens on the chosen audio and gives the focus back to its button`() {
        show(J3Samples.film)
        press(Key.DirectionRight, times = 3)
        focusable(DetailsPreview.TRACKS).assertIsFocused()
        press(Key.DirectionCenter)
        // The version chosen has the focus; down to the English audio track.
        focusable(panelLine(2)).assertIsFocused()
        // The second version, then over the title of the audio section.
        press(Key.DirectionDown, times = 2)
        focusable(panelLine(5)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(1), recorder.audio)
        compose.pressBack()
        focusable(DetailsPreview.TRACKS).assertIsFocused()
    }

    @Test
    fun `a series offers its next episode, then its seasons and episodes`() {
        show(J3Samples.series)
        focusable(DetailsPreview.PLAY).assertIsFocused()
        compose.onNode(hasText("Reprendre S01E02 — 24\u00A0min restantes")).assertExists()
        press(Key.DirectionDown)
        focusable(DetailsPreview.season(0)).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertEquals(listOf(ItemId("Les Marées-s2")), recorder.seasons)
        press(Key.DirectionDown)
        focusable(DetailsPreview.episode(0)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("Grande marée", recorder.opened.single().title)
    }

    @Test
    fun `a single season has no tabs`() {
        show(J3Samples.singleSeason)
        compose.onNode(hasText("Lire S01E02")).assertExists()
        compose.onNode(hasText("Saison 1")).assertDoesNotExist()
        press(Key.DirectionDown)
        focusable(DetailsPreview.episode(0)).assertIsFocused()
    }

    @Test
    fun `a person opens on the first work`() {
        show(J3Samples.person)
        focusable(DetailsPreview.card("movies", 0)).assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(recorder.opened.single().title == "Le Phare")
    }
}
