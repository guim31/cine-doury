package fr.guim.cinedoury.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.MediaItem
import fr.guim.cinedoury.core.ui.RailTags
import fr.guim.cinedoury.core.ui.RailTarget
import fr.guim.cinedoury.feature.home.HomeNavigation
import fr.guim.cinedoury.feature.home.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Essai réel 5, E: each entry of the rail says its name to accessibility (TalkBack, and the benchmarks
 * that look for « Films » through it), closed or open, and only once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5RailTest {
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

    private fun description(tag: String) =
        compose
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString()

    private fun assertNames(state: String) {
        assertEquals("Recherche", description(RailTags.SEARCH), state)
        assertEquals("Accueil", description(RailTags.HOME), state)
        assertEquals("Films", description(RailTags.library(J2Samples.films.id.value)), state)
        assertEquals("Camille", description(RailTags.PROFILES), state)
        assertEquals("Réglages", description(RailTags.SETTINGS), state)
        // Read once: the transparent text next to the icon says nothing more.
        assertEquals(
            0,
            compose.onAllNodesWithText("Réglages", useUnmergedTree = true).fetchSemanticsNodes().size,
            state,
        )
    }

    @Test
    fun everyEntrySaysItsNameClosedOrOpen() {
        compose.setTvContent {
            CineTheme(ThemeVariant.Dark) {
                HomeScreen(state = J2Samples.home, sources = J2Samples.noImages, navigation = Navigation, onRetry = {})
            }
        }
        compose.waitForIdle()
        assertNames("the rail closed")
        // ← from « Lire »: the rail opens on « Accueil ».
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        assertNames("the rail open")
    }
}

private const val SETTLE_MILLIS = 1_000L
