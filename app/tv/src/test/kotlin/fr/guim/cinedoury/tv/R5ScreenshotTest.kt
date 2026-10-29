package fr.guim.cinedoury.tv

import fr.guim.cinedoury.core.designsystem.theme.LayoutSize
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.player.PlayerPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Essai réel 5 (`SPEC.md` § 4.23), in the default size, dark theme, numbered from 220
 * (`docs/screenshots/r5/`, with the « avant » of each point).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class R5ScreenshotTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun shoot(
        name: String,
        focused: String?,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = compose.shootJ8(name, ThemeVariant.Dark, 1f, focused, LayoutSize.Default, content)

    /** A: 25 s before the end of an episode without credits, the card shows without a countdown yet. */
    @Test
    fun nextEpisodeWithoutCredits() =
        shoot("220-lecteur-episode-suivant-sans-generique", PlayerPreview.NEXT) {
            J4Player(R5Samples.endWithoutCredits)
        }

    /** A, before: the same moment in 0.9.1, the countdown already running out 20 s before the end. */
    @Test
    fun nextEpisodeWithoutCreditsBefore() =
        shoot("220-avant-lecteur-episode-suivant-sans-generique", PlayerPreview.NEXT) {
            J4Player(R5Samples.endWithoutCreditsBefore)
        }

    /** B: an episode, « Épisode précédent » and « Épisode suivant » on each side of « Lecture ». */
    @Test
    fun episodeControls() =
        shoot("221-lecteur-episode-precedent-suivant", PlayerPreview.PREVIOUS_EPISODE) {
            J4Player(R5Samples.episodePlaying, osd = true)
        }

    /** B, before: the controls of an episode in 0.9.1. */
    @Test
    fun episodeControlsBefore() =
        shoot("221-avant-lecteur-episode-precedent-suivant", PlayerPreview.PLAY) {
            J4Player(R5Samples.episodePlaying.copy(previous = null, next = null), osd = true)
        }
}

/** The samples of the essai réel 5. */
object R5Samples {
    /** An episode of « Les Marées » in its middle, with an episode before and after it. */
    val episodePlaying =
        J4Samples.credits.copy(
            position = 21.minutes,
            nextShown = false,
            nextCountdown = null,
            nextSeconds = null,
            segments = emptyList(),
            previous = J3Samples.mareesPlayable[0],
        )

    val endWithoutCredits =
        episodePlaying.copy(position = 42.minutes - 25.seconds, nextShown = true)

    val endWithoutCreditsBefore =
        endWithoutCredits.copy(nextCountdown = 0.5f, nextSeconds = 5)
}
