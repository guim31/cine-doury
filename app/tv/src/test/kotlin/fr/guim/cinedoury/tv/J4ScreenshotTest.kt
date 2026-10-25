package fr.guim.cinedoury.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.image.RemoteImage
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.model.ImageType
import fr.guim.cinedoury.core.model.PlaybackFailure
import fr.guim.cinedoury.core.ui.LocalClock
import fr.guim.cinedoury.feature.player.DeviceProfileScreen
import fr.guim.cinedoury.feature.player.PlaybackSettingsPreview
import fr.guim.cinedoury.feature.player.PlaybackSettingsScreen
import fr.guim.cinedoury.feature.player.PlayerError
import fr.guim.cinedoury.feature.player.PlayerPanel
import fr.guim.cinedoury.feature.player.PlayerPreview
import fr.guim.cinedoury.feature.player.PlayerScreen
import fr.guim.cinedoury.feature.player.PlayerState
import fr.guim.cinedoury.feature.player.SubtitleSettingsScreen
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
    fun playbackSettings() =
        shoot("101-reglages-lecture-et-son", PlaybackSettingsPreview.row(1)) {
            PlaybackSettingsScreen(J4Samples.settings, onChange = {})
        }

    @Test
    fun subtitleSettings() =
        shoot("102-reglages-apparence-sous-titres", PlaybackSettingsPreview.row(0)) {
            SubtitleSettingsScreen(J4Samples.subtitleStyle, onChange = {})
        }

    @Test
    fun deviceProfile() =
        shoot(
            "103-profil-de-l-appareil",
            "device-profile-0",
        ) { DeviceProfileScreen(J4Samples.settings) }

    @Composable
    private fun Player(
        state: PlayerState,
        osd: Boolean = false,
        panel: PlayerPanel? = null,
    ) {
        PlayerScreen(
            state = state,
            actions = J4Samples.noActions,
            sources = SampleArt.sources,
            onBack = {},
            previewOsd = osd,
            previewPanel = panel,
            video = {
                RemoteImage(
                    source = SampleArt.sources(SampleArt.image("${state.item.title} ", ImageType.Backdrop)),
                    blurHash = null,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
    }

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CompositionLocalProvider(LocalPreviewFocus provides focused, LocalClock provides J3Samples.clock) {
                    SampleArt.Provide(content)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name-${variant.slug}.png")
    }

    companion object {
        private const val SETTLE_MILLIS = 1_000L

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = ThemeVariant.entries.map { arrayOf(it) }
    }
}
