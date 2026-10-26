package fr.guim.cinedoury.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import fr.guim.cinedoury.core.designsystem.component.MESSAGE_DETAILS
import fr.guim.cinedoury.core.designsystem.component.MESSAGE_DETAILS_BUTTON
import fr.guim.cinedoury.core.designsystem.theme.CineTheme
import fr.guim.cinedoury.core.designsystem.theme.ThemeVariant
import fr.guim.cinedoury.core.source.SourceException
import fr.guim.cinedoury.feature.auth.AddressMode
import fr.guim.cinedoury.feature.auth.AddressScreen
import fr.guim.cinedoury.feature.auth.AddressState
import fr.guim.cinedoury.feature.auth.PasswordScreen
import fr.guim.cinedoury.feature.auth.PasswordState
import fr.guim.cinedoury.feature.auth.ProfilesScreen
import fr.guim.cinedoury.feature.auth.ProfilesState
import fr.guim.cinedoury.feature.auth.ServerSearchScreen
import fr.guim.cinedoury.feature.auth.ServerSearchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The defects of the real-device trial of 25/10/2026 that the remote shows (`CLAUDE.md`,
 * « Pièges »): text fields that kept the focus, OK that typed a letter, the first server found
 * left without the focus, an error detail out of reach, a hidden account not explained.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = TV_1080P)
class RemoteInputTest {
    @get:Rule
    val compose = createTvComposeRule()

    private fun press(key: Key) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setTvContent { CineTheme(ThemeVariant.Dark, content = content) }
        compose.waitForIdle()
    }

    private fun inside(tag: String) = hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))

    /** The frame of a field holds the focus: the D-pad moves on from it. */
    private fun assertFrameFocused(field: String) =
        compose.onNode(isFocused() and !hasSetTextAction() and hasAnyAncestor(hasTestTag(field))).assertExists()

    /** The input of a field holds the focus: the keyboard is open. */
    private fun assertTyping(field: String) = compose.onNode(hasSetTextAction() and inside(field)).assertIsFocused()

    private fun input(field: String) = compose.onNode(hasSetTextAction() and inside(field))

    @Test
    fun `password form - down and up move between the fields, typing or not`() {
        var state by mutableStateOf(PasswordState(username = ""))
        var submitted = 0
        show {
            PasswordScreen(
                state = state,
                onUsername = { state = state.copy(username = it) },
                onPassword = { state = state.copy(password = it) },
                onToggleShow = {},
                onSubmit = { submitted++ },
                onDismissError = {},
            )
        }
        assertFrameFocused(USERNAME)
        press(Key.DirectionDown)
        assertFrameFocused(PASSWORD)
        press(Key.DirectionUp)
        assertFrameFocused(USERNAME)

        // OK starts the typing and types nothing.
        press(Key.DirectionCenter)
        assertTyping(USERNAME)
        assertEquals("", state.username)
        input(USERNAME).performTextInput("admin")
        compose.waitForIdle()
        assertEquals("admin", state.username)

        // Down leaves the field while typing: no trap.
        press(Key.DirectionDown)
        assertFrameFocused(PASSWORD)
        press(Key.DirectionUp)
        assertFrameFocused(USERNAME)
        assertEquals(0, submitted)
    }

    @Test
    fun `password form - Next on the username, Done on the password signs in`() {
        var state by mutableStateOf(PasswordState(username = ""))
        var submitted = 0
        show {
            PasswordScreen(
                state = state,
                onUsername = { state = state.copy(username = it) },
                onPassword = { state = state.copy(password = it) },
                onToggleShow = {},
                onSubmit = { submitted++ },
                onDismissError = {},
            )
        }
        press(Key.DirectionCenter)
        input(USERNAME).performTextInput("admin")
        input(USERNAME).performImeAction()
        compose.waitForIdle()
        assertFrameFocused(PASSWORD)

        press(Key.DirectionCenter)
        assertTyping(PASSWORD)
        input(PASSWORD).performTextInput("secret")
        input(PASSWORD).performImeAction()
        compose.waitForIdle()
        assertEquals("secret", state.password)
        assertEquals(1, submitted)
    }

    @Test
    fun `address field - OK types nothing, Go submits, down reaches the button`() {
        var state by mutableStateOf(AddressState(mode = AddressMode.Add))
        var submitted = 0
        show {
            AddressScreen(
                state = state,
                onEdit = { state = state.copy(text = it) },
                onSubmit = { submitted++ },
                onDismissError = {},
            )
        }
        assertFrameFocused(ADDRESS)
        press(Key.DirectionCenter)
        assertTyping(ADDRESS)
        assertEquals("", state.text)
        input(ADDRESS).performTextInput("192.0.2.10")
        input(ADDRESS).performImeAction()
        compose.waitForIdle()
        assertEquals(1, submitted)
        assertFrameFocused(ADDRESS)
        press(Key.DirectionDown)
        compose.onNode(hasClickAction() and inside("address-submit")).assertIsFocused()
    }

    /** Counts the times the fields close the keyboard (Compose opens it through its input session). */
    private class RecordingKeyboard : SoftwareKeyboardController {
        var hidden = 0

        override fun show() = Unit

        override fun hide() {
            hidden++
        }
    }

    @Test
    fun `the keyboard closes with the typing - Go, then down out of the field`() {
        // Android 11 (the Shield) left it open over the next screen, where it took the OK of the
        // remote (essai réel 2): the field closes it itself.
        val keyboard = RecordingKeyboard()
        var state by mutableStateOf(AddressState(mode = AddressMode.Add))
        show {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                AddressScreen(
                    state = state,
                    onEdit = { state = state.copy(text = it) },
                    onSubmit = {},
                    onDismissError = {},
                )
            }
        }
        press(Key.DirectionCenter)
        assertTyping(ADDRESS)
        input(ADDRESS).performTextInput("192.0.2.10")
        val beforeGo = keyboard.hidden
        input(ADDRESS).performImeAction()
        compose.waitForIdle()
        assertTrue(keyboard.hidden > beforeGo)

        press(Key.DirectionCenter)
        assertTyping(ADDRESS)
        val beforeDown = keyboard.hidden
        press(Key.DirectionDown)
        assertTrue(keyboard.hidden > beforeDown)
    }

    @Test
    fun `a server found while waiting takes the focus`() {
        var state by mutableStateOf(ServerSearchState())
        show { servers(state) }
        compose.onNode(hasClickAction() and inside("enter-address")).assertIsFocused()

        compose.runOnIdle { state = ServerSearchState(J1Samples.servers.take(1), searching = true) }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and inside("server-0")).assertIsFocused()
    }

    @Test
    fun `a server found after a key press leaves the focus alone`() {
        var state by mutableStateOf(ServerSearchState())
        show { servers(state) }
        press(Key.DirectionLeft)

        compose.runOnIdle { state = ServerSearchState(J1Samples.servers.take(1), searching = true) }
        compose.waitForIdle()
        compose.onNode(hasClickAction() and inside("enter-address")).assertIsFocused()
    }

    @Composable
    private fun servers(state: ServerSearchState) =
        ServerSearchScreen(
            state,
            onChoose = {},
            onEnterAddress = {},
            onSearchAgain = {},
            onRetry = {},
            onDismissError = {},
        )

    @Test
    fun `more info opens a focused detail with the request and the HTTP status`() {
        val error =
            SourceException
                .Unexpected(IllegalStateException("Network read on the main thread"))
                .at("POST /Users/AuthenticateByName", 200)
        show {
            ServerSearchScreen(
                ServerSearchState(searching = false, error = error),
                onChoose = {},
                onEnterAddress = {},
                onSearchAgain = {},
                onRetry = {},
                onDismissError = {},
            )
        }
        // « Réessayer » has the focus; « Retour », then « Plus d'infos ».
        compose.onNode(isFocused() and hasClickAction()).assertExists()
        repeat(2) { press(Key.DirectionRight) }
        compose.onNode(hasClickAction() and inside(MESSAGE_DETAILS_BUTTON)).assertIsFocused()
        press(Key.DirectionCenter)

        val details = compose.onNode(hasTestTag(MESSAGE_DETAILS))
        details.assertIsFocused()
        val text =
            details
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
                .joinToString()
        assertTrue("SourceException.Unexpected" in text, text)
        assertTrue("Requête : POST /Users/AuthenticateByName" in text, text)
        assertTrue("Code HTTP : 200" in text, text)
        assertTrue("java.lang.IllegalStateException: Network read on the main thread" in text, text)
        press(Key.DirectionUp)
        compose.onNode(hasClickAction() and inside(MESSAGE_DETAILS_BUTTON)).assertIsFocused()
    }

    @Test
    fun `profiles that arrive from the server get the first focus`() {
        var state by mutableStateOf(ProfilesState(emptyList(), loading = true))
        show {
            ProfilesScreen(
                state,
                avatarUrl = { null },
                onChoose = {},
                onOtherAccount = {},
                onPassword = {},
                onRetry = {},
            )
        }
        // The server is asked: no focus yet, rather than on « Autre compte ».
        compose.onAllNodes(isFocused()).assertCountEquals(0)

        compose.runOnIdle { state = ProfilesState(J1Samples.profiles, loading = false) }
        compose.waitForIdle()
        val first = compose.onNode(hasClickAction() and inside("profile-0"))
        val focused = first.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true
        assertTrue(focused, compose.onRoot().printToString())
    }

    @Test
    fun `no profile listed - the screen says why and offers both ways to sign in`() {
        var quickConnect = false
        var password = false
        show {
            ProfilesScreen(
                ProfilesState(emptyList(), loading = false),
                avatarUrl = { null },
                onChoose = {},
                onOtherAccount = { quickConnect = true },
                onPassword = { password = true },
                onRetry = {},
            )
        }
        compose.onNode(hasText("Votre compte n'apparaît pas", substring = true)).assertExists()
        compose.onNode(hasClickAction() and inside("profile-other")).assertIsFocused()
        press(Key.DirectionDown)
        compose.onNode(hasClickAction() and inside("profiles-quick-connect")).assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionRight)
        press(Key.DirectionCenter)
        assertTrue(quickConnect && password)
    }

    private companion object {
        const val USERNAME = "username-field"
        const val PASSWORD = "password-field"
        const val ADDRESS = "address-field"
    }
}
