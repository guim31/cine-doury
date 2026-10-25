package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import fr.guim.cinedoury.core.data.ConnectionMode
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.source.ServerAddress
import fr.guim.cinedoury.feature.auth.PinPurpose
import fr.guim.cinedoury.feature.auth.PinScreen
import fr.guim.cinedoury.feature.auth.PinState
import fr.guim.cinedoury.feature.auth.ProfilesScreen
import fr.guim.cinedoury.feature.auth.ProfilesState
import fr.guim.cinedoury.feature.auth.ServerSearchScreen
import fr.guim.cinedoury.feature.auth.ServerSearchState
import fr.guim.cinedoury.feature.auth.WelcomeScreen
import fr.guim.cinedoury.feature.settings.SettingsScreen
import fr.guim.cinedoury.feature.settings.SettingsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The J1 screens work with the D-pad alone, each with an obvious first focus (`UI.md` § 2). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class J1NavigationTest {
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
    fun `welcome starts on its only button`() {
        var started = false
        show { WelcomeScreen(onStart = { started = true }) }
        focusable("start").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(true, started)
    }

    @Test
    fun `servers found are reached from the address button`() {
        var chosen: ServerAddress? = null
        var typed = false
        show {
            ServerSearchScreen(
                ServerSearchState(J1Samples.servers, searching = false),
                onChoose = { chosen = it },
                onEnterAddress = { typed = true },
                onSearchAgain = {},
                onRetry = {},
                onDismissError = {},
            )
        }
        focusable("enter-address").assertIsFocused()
        press(Key.DirectionUp)
        focusable("server-1").assertIsFocused()
        press(Key.DirectionUp)
        press(Key.DirectionCenter)
        assertEquals(J1Samples.local, chosen)
        assertEquals(false, typed)
    }

    @Test
    fun `profiles go left to right, other account last`() {
        var chosen: String? = null
        var other = false
        show {
            ProfilesScreen(
                ProfilesState(J1Samples.profiles, loading = false),
                avatarUrl = { null },
                onChoose = { chosen = it.user.name },
                onOtherAccount = { other = true },
                onRetry = {},
            )
        }
        focusable("profile-0").assertIsFocused()
        press(Key.DirectionRight)
        focusable("profile-1").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals("Léo", chosen)
        repeat(2) { press(Key.DirectionRight) }
        focusable("profile-other").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(true, other)
    }

    @Test
    fun `PIN typed on the pad and with the number keys`() {
        val typed = StringBuilder()
        var erased = 0
        show {
            PinScreen(
                PinState(PinPurpose.Unlock, J1Samples.profiles.first()),
                onDigit = { typed.append(it) },
                onErase = { erased++ },
            )
        }
        focusable("key-5").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionUp)
        focusable("key-2").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.Eight)
        press(Key.NumPad0)
        assertEquals("5280", typed.toString())
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionRight)
        focusable("key-erase").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, erased)
    }

    @Test
    fun `forgetting the server asks first, the focus on Cancel`() {
        var forgotten = false
        show {
            SettingsScreen(
                state = SettingsState(J1Samples.session(ConnectionMode.Local)),
                showCatalog = false,
                navigation = NoNavigation,
                onToggleStartup = {},
                onSwitchProfile = {},
                onSignOut = {},
                onForgetServer = { forgotten = true },
            )
        }
        focusable("pin-row").assertIsFocused()
        focusable("forget-server").performClickAndWait()
        compose
            .onNode(
                isFocused() and hasText("Annuler") and hasAnyAncestor(hasTestTag("forget-dialog")),
            ).assertExists()
        press(Key.DirectionCenter)
        assertEquals(false, forgotten)
        compose.onNode(hasTestTag("forget-dialog")).assertDoesNotExist()
    }

    private fun SemanticsNodeInteraction.performClickAndWait() {
        performClick()
        compose.waitForIdle()
    }
}
