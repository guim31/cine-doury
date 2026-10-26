package fr.guim.cinedoury.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.media3.common.text.Cue
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.feature.player.DeviceProfileScreen
import fr.guim.cinedoury.feature.player.PlayerError
import fr.guim.cinedoury.feature.player.PlayerPanel
import fr.guim.cinedoury.feature.player.PlayerPreview
import fr.guim.cinedoury.feature.player.PlayerScreen
import fr.guim.cinedoury.feature.player.PlayerState
import fr.guim.cinedoury.feature.player.SubtitleLayer
import fr.guim.cinedoury.feature.player.playerPanelLine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of J4, 1920 × 1080, dark and OLED black (`UI.md` § 12): the interface of
 * the player over a still picture, the search and its preview, the panels (tracks, quality,
 * settings), « Passer l'intro », « Épisode suivant », « Vous êtes toujours là ? », the information
 * screen played directly and transcoded, an error, and the settings of the device. The focused
 * element is drawn through `LocalPreviewFocus`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J4ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun osd() = shoot("90-lecteur-interface", PlayerPreview.PROGRESS) { Player(J4Samples.playing, osd = true) }

    @Test
    fun search() =
        shoot("91-lecteur-recherche-vignette", PlayerPreview.PROGRESS) {
            Player(J4Samples.searching, osd = true)
        }

    @Test
    fun tracks() =
        shoot("92-lecteur-audio-et-sous-titres", playerPanelLine(0, J4Samples.chosenAudioRow)) {
            Player(J4Samples.playing, panel = PlayerPanel.Tracks)
        }

    @Test
    fun quality() =
        shoot("93-lecteur-qualite", playerPanelLine(0, 0)) {
            Player(J4Samples.playing, panel = PlayerPanel.Quality)
        }

    @Test
    fun settings() =
        shoot("94-lecteur-reglages-sous-titres", playerPanelLine(0, 2)) {
            Player(J4Samples.paused, panel = PlayerPanel.Settings)
        }

    @Test
    fun skipIntro() = shoot("95-lecteur-passer-intro", PlayerPreview.SKIP) { Player(J4Samples.intro) }

    @Test
    fun nextEpisode() = shoot("96-lecteur-episode-suivant", PlayerPreview.NEXT) { Player(J4Samples.credits) }

    @Test
    fun stillWatching() = shoot("97-lecteur-toujours-la", PlayerPreview.STILL) { Player(J4Samples.stillWatching) }

    @Test
    fun directInfo() = shoot("98-lecteur-infos-lecture-directe", null) { Player(J4Samples.directInfo) }

    @Test
    fun transcodedInfo() = shoot("99-lecteur-infos-transcodage", null) { Player(J4Samples.transcodedInfo) }

    @Test
    fun error() =
        shoot("100-lecteur-erreur", PlayerPreview.RETRY) {
            Player(
                J4Samples.playing.copy(
                    error =
                        PlayerError.Playback(
                            PlaybackFailure.Decoder,
                            "ERROR_CODE_DECODING_FAILED: OMX.Nvidia.h265.decode",
                        ),
                    playing = false,
                ),
            )
        }

    @Test
    fun deviceProfile() =
        shoot(
            "103-profil-de-l-appareil",
            "device-profile-0",
        ) { DeviceProfileScreen(J4Samples.settings) }

    @Test
    fun subtitleSize() =
        shoot("104-lecteur-taille-sous-titres", playerPanelLine(0, 0)) {
            Player(J4Samples.resizing, panel = PlayerPanel.SubtitleSize)
        }

    @Test
    fun subtitlesAt100() =
        shoot("105-sous-titres-100", null) { Player(J4Samples.withSubtitles(1f), cue = J4Samples.SUBTITLE_LINE) }

    @Test
    fun subtitlesAt160() =
        shoot("106-sous-titres-160", null) { Player(J4Samples.withSubtitles(1.6f), cue = J4Samples.SUBTITLE_LINE) }

    @Test
    fun burnedInSubtitles() =
        shoot("107-lecteur-sous-titres-incrustes", playerPanelLine(0, 0)) {
            Player(J4Samples.burnedIn, panel = PlayerPanel.SubtitleSize)
        }

    @Composable
    private fun Player(
        state: PlayerState,
        osd: Boolean = false,
        panel: PlayerPanel? = null,
        cue: String? = null,
    ) = J4Player(state, osd, panel, cue)

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) = compose.shootJ4(name, variant, focused, content)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}

/**
 * The subtitles at 160 % on a 4K output (3840 × 2160, the same 960 × 540 dp at four times the
 * density): they must take the same part of the picture as in 1080p (`106-sous-titres-160`).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_4K)
class J4Screenshot4kTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun subtitlesAt160In4k() =
        compose.shootJ4("108-sous-titres-160-4k", variant, null) {
            J4Player(J4Samples.withSubtitles(1.6f), cue = J4Samples.SUBTITLE_LINE)
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(arrayOf(ThemeVariant.entries.first()))
    }
}

/** The player over a still picture; [cue] is a subtitle on screen, drawn by Media3. */
@Composable
internal fun J4Player(
    state: PlayerState,
    osd: Boolean = false,
    panel: PlayerPanel? = null,
    cue: String? = null,
) {
    PlayerScreen(
        state = state,
        actions = J4Samples.noActions,
        sources = SampleArt.sources,
        onBack = {},
        previewOsd = osd,
        previewPanel = panel,
        video = { sample ->
            RemoteImage(
                source = SampleArt.sources(SampleArt.image("${state.item.title} ", ImageType.Backdrop)),
                blurHash = null,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            SubtitleLayer(
                cues = listOfNotNull(cue?.let { Cue.Builder().setText(it).build() }),
                style = state.settings.subtitles,
                modifier = Modifier.fillMaxSize(),
                sample = sample,
            )
        },
    )
}

private const val SETTLE_MILLIS = 1_000L

internal fun ComposeContentTestRule.shootJ4(
    name: String,
    variant: ThemeVariant,
    focused: String?,
    content: @Composable () -> Unit,
) {
    setContent {
        CineTheme(variant = variant) {
            CompositionLocalProvider(LocalPreviewFocus provides focused, LocalClock provides J3Samples.clock) {
                SampleArt.Provide(content)
            }
        }
    }
    waitForIdle()
    mainClock.advanceTimeBy(SETTLE_MILLIS)
    waitForIdle()
    onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
}
