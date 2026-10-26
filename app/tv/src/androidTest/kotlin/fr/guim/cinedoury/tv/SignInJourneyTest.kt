package fr.guim.cinedoury.tv

import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue
import fr.guim.cinedoury.feature.auth.R as AuthR
import fr.guim.cinedoury.feature.home.R as HomeR

/**
 * The sign-in of a real user, from the first launch to the home, against the Jellyfin of the CI
 * (`ci/jellyfin`, reached from the emulator at 10.0.2.2), on the real Android: its Keystore, its
 * network rules (no network on the main thread) and its input pipeline. The remote is simulated
 * by key events sent through the instrumentation, from a virtual keyboard as a Bluetooth remote
 * may be, and the keyboard by text and IME actions. The real-device trial of 25/10/2026 failed at
 * three steps of this path. It ends in the player of J4, opened from the home.
 */
@RunWith(AndroidJUnit4::class)
class SignInJourneyTest {
    // The activity is launched by the test, once out of touch mode (see below).
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** The keys that reached the activity, and whether it used them, for the log of a failure. */
    private val keys = Collections.synchronizedList(mutableListOf<String>())

    private class KeyRecorder(
        private val window: Window.Callback,
        private val keys: MutableList<String>,
    ) : Window.Callback by window {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            window.dispatchKeyEvent(event).also { handled ->
                val action = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
                keys += "${KeyEvent.keyCodeToString(event.keyCode)} $action handled=$handled"
            }
    }

    private val server = InstrumentationRegistry.getArguments().getString("jellyfinUrl") ?: "http://10.0.2.2:8096"
    private val password = InstrumentationRegistry.getArguments().getString("jellyfinPassword") ?: "cinedoury-test"

    /** A key of the remote, through the whole input pipeline of Android (IME stage included). */
    private fun remote(keyCode: Int) {
        compose.waitForIdle()
        // In the log of the CI, with the focus changes of the window: a key lost on its way shows.
        record("sent ${KeyEvent.keyCodeToString(keyCode)}, window focus: ${windowFocus.get()}")
        instrumentation.sendKeyDownUpSync(keyCode)
        compose.waitForIdle()
    }

    /** The window's focus, as its main thread last saw it. */
    private val windowFocus = AtomicBoolean(false)

    private fun record(event: String) {
        keys += event
        Log.i(LOG_TAG, event)
    }

    private fun waitFor(
        matcher: SemanticsMatcher,
        timeout: Long = TIMEOUT,
    ) {
        try {
            compose.waitUntil(timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            // What the screen shows, where the focus is, and whether a keyboard is open (it would
            // take OK), for the log of the CI.
            throw AssertionError(
                "Not found: ${matcher.description}\n" + compose.onRoot().printToString() + "\n" + inputState(),
                timeout,
            )
        }
    }

    /** Whether the keyboard is shown over the app (null without an activity). */
    private fun keyboardShown(): Boolean? {
        var shown: Boolean? = null
        scenario?.onActivity {
            shown =
                it.window.decorView.rootWindowInsets
                    ?.isVisible(WindowInsets.Type.ime())
        }
        return shown
    }

    /** Whether the keyboard is shown, and which view holds the focus of the window. */
    private fun inputState(): String {
        var state = "no activity"
        scenario?.onActivity { activity ->
            val window = activity.window.decorView
            val keyboard = window.rootWindowInsets?.isVisible(WindowInsets.Type.ime())
            val imm = activity.getSystemService(InputMethodManager::class.java)
            val view = activity.currentFocus
            state = "keyboard shown: $keyboard, input method active: ${view?.let { imm.isActive(it) }}, " +
                "accepting text: ${imm.isAcceptingText}, window focus: ${window.hasWindowFocus()}, " +
                "touch mode: ${window.isInTouchMode}, focused view: ${activity.currentFocus?.javaClass?.name}\n" +
                "keys: ${synchronized(keys) { keys.takeLast(KEYS_SHOWN).joinToString() }}"
        }
        return state
    }

    private fun appeared(
        matcher: SemanticsMatcher,
        timeout: Long,
    ): Boolean = appeared(timeout) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun appeared(
        timeout: Long,
        condition: () -> Boolean,
    ): Boolean =
        try {
            compose.waitUntil(timeout, condition)
            true
        } catch (_: ComposeTimeoutException) {
            false
        }

    private fun focusedText(text: String) = isFocused() and hasText(text, substring = true)

    /** A label of the app in the language of the device (the emulator of the CI is in English). */
    private fun text(id: Int): String = instrumentation.targetContext.getString(id)

    private val start get() = text(AuthR.string.welcome_start)

    /** The Quick Connect code came from the server: digits instead of the placeholder dashes. */
    private fun hasDigits() =
        SemanticsMatcher("has digits") { node ->
            node.config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { line -> line.any(Char::isDigit) }
        }

    /** Typing in the focused field: the text, then the action of the keyboard. */
    private fun type(text: String) {
        waitFor(isFocused() and hasSetTextAction())
        // OK opened the keyboard: a hand types on it once it is there. Typing and confirming before
        // it shows (1 s after OK, a keyboard starting cold on the emulator) crosses its opening with
        // its closing, and the keyboard, still settling, took the next key.
        val opened = appeared(TIMEOUT) { keyboardShown() == true }
        assertTrue(opened, "OK in the field does not open the keyboard: ${inputState()}")
        val input = compose.onNode(isFocused() and hasSetTextAction())
        input.performTextInput(text)
        input.performImeAction()
        compose.waitForIdle()
    }

    @Test
    fun fromTheFirstLaunchToTheHomeWithTheRemote() {
        // An instrumented activity starts in touch mode, where Compose gives no focus to clickable
        // elements; a TV has no touch screen. Out of touch mode first, then a key of the remote that
        // does nothing on the first screen, as a key pressed in touch mode only leaves it.
        instrumentation.setInTouchMode(false)
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            it.onActivity { activity ->
                activity.window.callback = KeyRecorder(activity.window.callback, keys)
                val window = activity.window.decorView
                windowFocus.set(window.hasWindowFocus())
                window.viewTreeObserver.addOnWindowFocusChangeListener { focused ->
                    windowFocus.set(focused)
                    record("window focus: $focused")
                }
            }
            journey()
        }
    }

    private fun journey() {
        waitFor(hasText(start) and hasClickAction())
        remote(KeyEvent.KEYCODE_DPAD_DOWN)
        // The first focus is the one thing not given by the remote: an instrumented window may still
        // be in touch mode when the screen asks for it.
        if (!appeared(focusedText(start), FIRST_FOCUS_TIMEOUT)) {
            compose.onNode(hasText(start) and hasClickAction()).requestFocus()
        }
        waitFor(focusedText(start))
        remote(KeyEvent.KEYCODE_DPAD_CENTER)

        // No server answers the discovery from the emulator: the address is typed.
        waitFor(focusedText(text(AuthR.string.servers_enter_address)), DISCOVERY_TIMEOUT)
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(isFocused() and hasAnyAncestor(hasTestTag("address-field")))
        // OK starts the typing and types nothing (no stray letter).
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        type(server)

        // « Qui regarde ? »: the public users, and the line about hidden accounts.
        waitFor(hasText("enfant"))
        waitFor(hasText(text(AuthR.string.profiles_hidden_hint)))
        // Sorted by name, « admin » first; its name sits under the focused avatar.
        waitFor(isFocused() and hasAnyAncestor(hasTestTag("profile-0")))
        // The keyboard of the address closed with its typing: left open (Android 11, the Shield),
        // it took the OK meant for the profile (essai réel 2).
        val closed = appeared(TIMEOUT) { keyboardShown() != true }
        assertTrue(closed, "the keyboard stays open over the profiles: ${inputState()}")
        remote(KeyEvent.KEYCODE_DPAD_CENTER)

        // Quick Connect asks the server for a code at once (it failed on the device).
        waitFor(hasTestTag("quick-connect-code") and hasDigits())
        waitFor(focusedText(text(AuthR.string.use_password)))
        remote(KeyEvent.KEYCODE_DPAD_CENTER)

        // The username is known: the focus is on the password.
        waitFor(isFocused() and hasAnyAncestor(hasTestTag("password-field")))
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        type(password)

        // Signed in, the token kept by the Keystore: the home of the administrator.
        waitFor(hasText(text(HomeR.string.row_resume)), SIGN_IN_TIMEOUT)

        // And on to the player of J4 with the session just made (PlaybackOnDeviceTest checks the
        // playback itself): the main button of the banner has the focus.
        val play = hasTestTag(HOME_PLAY) or hasAnyAncestor(hasTestTag(HOME_PLAY))
        waitFor(isFocused() and play)
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(hasTestTag(PLAYER), SIGN_IN_TIMEOUT)
        remote(KeyEvent.KEYCODE_BACK)
    }

    private companion object {
        const val LOG_TAG = "SignInJourney"
        const val TIMEOUT = 10_000L
        const val KEYS_SHOWN = 12
        const val FIRST_FOCUS_TIMEOUT = 3_000L
        const val DISCOVERY_TIMEOUT = 15_000L
        const val SIGN_IN_TIMEOUT = 30_000L
        const val HOME_PLAY = "home-play"
        const val PLAYER = "player"
    }
}
