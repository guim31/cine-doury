package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.guim.cinedoury.core.designsystem.component.LocalPreviewFocus
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.feature.settings.DeviceNameScreen
import fr.guim.cinedoury.feature.settings.DiagnosticPreview
import fr.guim.cinedoury.feature.settings.DiagnosticScreen
import fr.guim.cinedoury.feature.settings.LogsScreen
import fr.guim.cinedoury.feature.settings.SettingsPageScreen
import fr.guim.cinedoury.feature.settings.SettingsScreen
import fr.guim.cinedoury.feature.settings.SettingsSection
import fr.guim.cinedoury.feature.settings.page.SettingsPagePreview
import fr.guim.cinedoury.feature.update.UpdateActions
import fr.guim.cinedoury.feature.update.UpdatePreview
import fr.guim.cinedoury.feature.update.UpdateScreen
import fr.guim.cinedoury.feature.update.UpdateState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reference screenshots of J7, 1920 × 1080, dark and OLED black (`UI.md` § 12): the settings, a
 * page per theme and a choice open; the update in each of its states; the logs and the report.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = TV_1080P)
class J7ScreenshotTest(
    private val variant: ThemeVariant,
) {
    @get:Rule
    val compose = createTvComposeRule()

    @Test
    fun settings() =
        shoot("150-reglages", SettingsPagePreview.row(SettingsSection.Playback.name)) {
            SettingsScreen(J7Samples.settings, NoNavigation)
        }

    @Test
    fun playback() = page("151-reglages-lecture", SettingsSection.Playback, "seek-back")

    @Test
    fun playbackQuality() =
        page(
            "152-reglages-lecture-qualite",
            SettingsSection.Playback,
            SettingsPagePreview.option(0),
            choice = "remote-quality",
        )

    @Test
    fun picture() = page("153-reglages-image", SettingsSection.Picture, "frame-rate")

    @Test
    fun sound() = page("154-reglages-son", SettingsSection.Sound, "audio-language")

    @Test
    fun subtitles() = page("155-reglages-sous-titres", SettingsSection.Subtitles, "subtitle-mode")

    @Test
    fun segments() = page("156-reglages-intro-et-generique", SettingsSection.Segments, "segments")

    @Test
    fun browsing() = page("157-reglages-accueil-et-navigation", SettingsSection.Browsing, "customize-home")

    @Test
    fun libraries() =
        page("158-reglages-bibliotheques", SettingsSection.Libraries, "place-${J3Samples.libraries[1].id.value}")

    @Test
    fun appearance() = page("159-reglages-apparence", SettingsSection.Appearance, "theme")

    @Test
    fun profile() = page("160-reglages-profil", SettingsSection.Profile, "pin")

    @Test
    fun network() = page("161-reglages-reseau", SettingsSection.Network, "address-local")

    @Test
    fun device() = page("162-reglages-appareil", SettingsSection.Device, "device-name")

    @Test
    fun advanced() = page("163-reglages-avance", SettingsSection.Advanced, "logs")

    @Test
    fun deviceName() =
        shoot("164-nom-de-l-appareil", "device-name-field") {
            DeviceNameScreen(current = "Salon", tvName = "SHIELD Android TV", onSave = {})
        }

    @Test
    fun updateAvailable() = update("165-mise-a-jour-disponible", J7Samples.available)

    @Test
    fun updateDownloading() = update("166-mise-a-jour-telechargement", J7Samples.downloading)

    @Test
    fun updateReady() = update("167-mise-a-jour-prete", J7Samples.ready)

    @Test
    fun updateSignature() = update("168-mise-a-jour-signature-differente", J7Samples.signature)

    @Test
    fun updatePermission() = update("169-mise-a-jour-autorisation-manquante", J7Samples.permission)

    @Test
    fun updateFailed() = update("170-mise-a-jour-erreur", J7Samples.failed)

    @Test
    fun updateUpToDate() = update("171-mise-a-jour-a-jour", J7Samples.upToDate)

    @Test
    fun updateDevelopment() = update("172-mise-a-jour-version-de-developpement", J7Samples.development)

    @Test
    fun report() =
        shoot("173-rapport-de-diagnostic", SettingsPagePreview.row(DiagnosticPreview.SEND)) {
            DiagnosticScreen(J7Samples.report, onSend = {})
        }

    @Test
    fun logs() = shoot("174-journaux", SettingsPagePreview.row("log-0")) { LogsScreen(entries = J7Samples.failures) }

    private fun page(
        name: String,
        section: SettingsSection,
        focused: String,
        choice: String? = null,
    ) = shoot(name, if (choice == null) SettingsPagePreview.row(focused) else focused) {
        SettingsPageScreen(
            section = section,
            state = J7Samples.settings,
            actions = NoSettingsActions,
            navigation = NoNavigation,
            showDebug = true,
            previewChoice = choice,
        )
    }

    private fun update(
        name: String,
        state: UpdateState,
    ) = shoot(name, UpdatePreview.PRIMARY) { UpdateScreen(state, NoUpdateActions) }

    private fun shoot(
        name: String,
        focused: String?,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            CineTheme(variant = variant) {
                CompositionLocalProvider(LocalPreviewFocus provides focused, content = content)
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

internal object NoUpdateActions : UpdateActions {
    override fun install() = Unit

    override fun confirm() = Unit

    override fun later() = Unit

    override fun ignore() = Unit

    override fun retry() = Unit

    override fun openPermissionSettings() = Unit

    override fun openAppSettings() = Unit
}
