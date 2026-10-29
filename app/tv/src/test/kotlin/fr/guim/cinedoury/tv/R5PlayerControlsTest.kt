package fr.guim.cinedoury.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.player.PlayerActions
import fr.guim.cinedoury.feature.player.PlayerPreview
import fr.guim.cinedoury.feature.player.PlayerScreen
import fr.guim.cinedoury.feature.player.PlayerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Essai réel 5, B: the buttons « Épisode précédent » and « Épisode suivant » of the player, there for
 * an episode with a neighbour only, said to TalkBack, and the media keys of the remote.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5PlayerControlsTest {
    @get:Rule
    val compose = createTvComposeRule()

    private val gestures = mutableListOf<String>()

    private val actions =
        object : PlayerActions by J4Samples.noActions {
            override fun playNext() {
                gestures += "next"
            }

            override fun playPrevious() {
                gestures += "previous"
            }

            override fun chapter(forward: Boolean) {
                gestures += if (forward) "chapter+" else "chapter-"
            }
        }

    private fun player(state: PlayerState) {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                PlayerScreen(
                    state = state,
                    actions = actions,
                    sources = SampleArt.sources,
                    onBack = {},
                    previewOsd = true,
                    video = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun shown(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    @Test
    fun anEpisodeBetweenTwoHasBothButtons() {
        player(R5Samples.episodePlaying)
        assertEquals(true, shown(PlayerPreview.PREVIOUS_EPISODE))
        assertEquals(true, shown(PlayerPreview.NEXT_EPISODE))
        // TalkBack reads their labels.
        compose.onNodeWithContentDescription(PREVIOUS).assertContentDescriptionEquals(PREVIOUS)
        compose.onNode(hasContentDescription(NEXT)).assertContentDescriptionEquals(NEXT)
        // The media keys of the remote do the same.
        press(Key.MediaNext)
        press(Key.MediaPrevious)
        assertEquals(listOf("next", "previous"), gestures)
    }

    @Test
    fun noButtonAtTheEndsOfTheSeriesNorForAFilm() {
        player(R5Samples.episodePlaying.copy(previous = null))
        assertEquals(false, shown(PlayerPreview.PREVIOUS_EPISODE), "the first episode")
        assertEquals(true, shown(PlayerPreview.NEXT_EPISODE))
        // Without an episode before it, the key goes back a chapter.
        press(Key.MediaPrevious)
        assertEquals(listOf("chapter-"), gestures)
    }

    @Test
    fun aFilmHasNeither() {
        player(J4Samples.playing)
        assertEquals(false, shown(PlayerPreview.PREVIOUS_EPISODE))
        assertEquals(false, shown(PlayerPreview.NEXT_EPISODE))
        press(Key.MediaNext)
        assertEquals(listOf("chapter+"), gestures)
    }
}

private const val PREVIOUS = "Épisode précédent"
private const val NEXT = "Épisode suivant"
