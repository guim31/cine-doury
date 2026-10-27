package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
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
import fr.guim.cinedoury.core.model.PlayerSettings
import fr.guim.cinedoury.feature.settings.SettingsActions
import fr.guim.cinedoury.feature.settings.SettingsNavigation
import fr.guim.cinedoury.feature.settings.SettingsPageScreen
import fr.guim.cinedoury.feature.settings.SettingsScreen
import fr.guim.cinedoury.feature.settings.SettingsSection
import fr.guim.cinedoury.feature.settings.page.SettingsPagePreview
import fr.guim.cinedoury.feature.update.UpdatePreview
import fr.guim.cinedoury.feature.update.UpdateScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings of J7 with the D-pad alone (`UI.md` § 2): each page opens on its first line, a choice
 * opens its values with the current one focused, OK chooses and the focus comes back to the line,
 * « Retour » closes the panel, ← and → step a value.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J7NavigationTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun focusable(tag: String) =
        compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))))

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setTvContent { CineTheme(ThemeVariant.Dark, content = content) }
        compose.waitForIdle()
    }

    @Test
    fun `the first page opens a page per theme, the focus on Lecture`() {
        val opened = mutableListOf<SettingsSection>()
        val navigation =
            object : SettingsNavigation by NoNavigation {
                override fun page(section: SettingsSection) {
                    opened += section
                }
            }
        show { SettingsScreen(J7Samples.settings, navigation) }
        focusable(SettingsPagePreview.row(SettingsSection.Playback.name)).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        focusable(SettingsPagePreview.row(SettingsSection.Picture.name)).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(SettingsSection.Playback, SettingsSection.Picture), opened)
    }

    @Test
    fun `every page opens on its first line`() {
        val first =
            mapOf(
                SettingsSection.Playback to "seek-back",
                SettingsSection.Picture to "frame-rate",
                SettingsSection.Sound to "audio-language",
                SettingsSection.Subtitles to "subtitle-mode",
                SettingsSection.Segments to "segments",
                SettingsSection.Browsing to "customize-home",
                SettingsSection.Libraries to "place-${J3Samples.libraries[0].id.value}",
                SettingsSection.Appearance to "theme",
                SettingsSection.Profile to "pin",
                SettingsSection.Network to "address-local",
                SettingsSection.Device to "device-name",
                SettingsSection.Advanced to "logs",
            )
        var section by mutableStateOf(SettingsSection.Playback)
        show {
            // A new page each time, as the app pushes a new screen.
            androidx.compose.runtime.key(section) {
                SettingsPageScreen(section, J7Samples.settings, NoSettingsActions, NoNavigation)
            }
        }
        SettingsSection.entries.forEach { page ->
            section = page
            compose.waitForIdle()
            focusable(SettingsPagePreview.row(first.getValue(page))).assertIsFocused()
        }
    }

    @Test
    fun `a choice opens on its value, OK chooses, the focus comes back to the line`() {
        var settings by mutableStateOf(J7Samples.settings)
        val actions =
            object : SettingsActions by NoSettingsActions {
                override fun updatePlayer(change: (PlayerSettings) -> PlayerSettings) {
                    settings = settings.copy(player = change(settings.player))
                }
            }
        show { SettingsPageScreen(SettingsSection.Playback, settings, actions, NoNavigation) }
        focusable(SettingsPagePreview.row("seek-back")).assertIsFocused()
        press(Key.DirectionDown)
        focusable(SettingsPagePreview.row("seek-forward")).assertIsFocused()
        press(Key.DirectionCenter)
        // 15 s, the value of the sample, is the third of 5, 10, 15, 30, 60.
        focusable(SettingsPagePreview.option(2)).assertIsFocused()
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        assertEquals(30, settings.player.seekForwardSeconds)
        focusable(SettingsPagePreview.row("seek-forward")).assertIsFocused()

        // « Retour » closes the panel without choosing.
        press(Key.DirectionCenter)
        focusable(SettingsPagePreview.option(3)).assertIsFocused()
        press(Key.DirectionDown)
        compose.pressBack()
        assertEquals(30, settings.player.seekForwardSeconds)
        focusable(SettingsPagePreview.row("seek-forward")).assertIsFocused()
    }

    @Test
    fun `left and right step the audio offset, OK switches a toggle`() {
        var settings by mutableStateOf(J7Samples.settings)
        val actions =
            object : SettingsActions by NoSettingsActions {
                override fun updatePlayer(change: (PlayerSettings) -> PlayerSettings) {
                    settings = settings.copy(player = change(settings.player))
                }
            }
        show { SettingsPageScreen(SettingsSection.Sound, settings, actions, NoNavigation) }
        press(Key.DirectionDown)
        focusable(SettingsPagePreview.row("stereo")).assertIsFocused()
        press(Key.DirectionCenter)
        assertTrue(settings.player.forceStereo)
        press(Key.DirectionDown)
        focusable(SettingsPagePreview.row("audio-offset")).assertIsFocused()
        press(Key.DirectionRight)
        press(Key.DirectionRight)
        press(Key.DirectionLeft)
        assertEquals(75, settings.player.audioOffsetMillis)
    }

    @Test
    fun `the update screen opens on Install, Later and Ignore beside it`() {
        var installed = 0
        val actions =
            object : fr.guim.cinedoury.feature.update.UpdateActions by NoUpdateActions {
                override fun install() {
                    installed++
                }
            }
        show { UpdateScreen(J7Samples.available, actions) }
        focusable(UpdatePreview.PRIMARY).assertIsFocused()
        press(Key.DirectionRight)
        focusable(UpdatePreview.LATER).assertIsFocused()
        press(Key.DirectionRight)
        focusable(UpdatePreview.IGNORE).assertIsFocused()
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        press(Key.DirectionCenter)
        assertEquals(1, installed)
    }
}
