package fr.guim.cinedoury.benchmark

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern

/** The app under test: the debug or the optimised APK, both `fr.guim.cinedoury`. */
internal const val PACKAGE = "fr.guim.cinedoury"

/** The test Jellyfin of the CI, as the emulator reaches it (`ci/jellyfin`). */
internal val server: String
    get() = InstrumentationRegistry.getArguments().getString("jellyfinUrl") ?: "http://10.0.2.2:8096"

private val password: String
    get() = InstrumentationRegistry.getArguments().getString("jellyfinPassword") ?: "cinedoury-test"

/**
 * The remote and the eyes of a user, from outside the app: key events through the whole input
 * pipeline of Android, and what the screen shows through accessibility (the test tags of the app are
 * resource ids, `MainActivity`). Nothing here knows a class of the app, which R8 shrinks.
 */
@Suppress("TooManyFunctions") // One function per gesture of the remote, or per look at the screen.
internal class Remote(
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()),
) {
    private val automation =
        InstrumentationRegistry.getInstrumentation().uiAutomation.apply {
            // The keyboard is a window of its own: seen only with the interactive windows.
            serviceInfo =
                serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        }

    /** A key pressed and released, then a pause as a hand leaves between two keys. */
    fun press(
        keyCode: Int,
        pause: Long = PAUSE,
    ) {
        device.pressKeyCode(keyCode)
        SystemClock.sleep(pause)
    }

    /**
     * A key held down for [millis]: the repeated key-downs a remote sends (one every 50 ms after the
     * first half second), then one key-up.
     */
    fun hold(
        keyCode: Int,
        millis: Long,
    ) {
        val down = SystemClock.uptimeMillis()
        inject(KeyEvent(down, down, KeyEvent.ACTION_DOWN, keyCode, 0))
        SystemClock.sleep(REPEAT_DELAY)
        var repeat = 1
        while (SystemClock.uptimeMillis() - down < millis) {
            inject(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, repeat++))
            SystemClock.sleep(REPEAT_INTERVAL)
        }
        inject(KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
        SystemClock.sleep(PAUSE)
    }

    private fun inject(event: KeyEvent) {
        check(automation.injectInputEvent(event, true)) { "key not injected: $event" }
    }

    /** The element tagged [tag], once it shows. */
    fun waitFor(
        tag: String,
        timeout: Long = TIMEOUT,
    ): UiObject2 = waitFor(By.res(tag), timeout)

    fun waitFor(
        selector: BySelector,
        timeout: Long = TIMEOUT,
    ): UiObject2 =
        device.wait(Until.findObject(selector), timeout)
            ?: throw AssertionError("Not found after $timeout ms: $selector\n${screen()}")

    fun has(tag: String): Boolean = device.hasObject(By.res(tag))

    /** Whether the focus is on the element tagged [tag] or inside it (a component of the design system). */
    fun focusedIn(tag: String): Boolean {
        val element = device.findObject(By.res(tag)) ?: return false
        return element.isFocused || element.hasObject(By.focused(true))
    }

    /**
     * The label of the focused element inside the element tagged [tag] (a rail entry): its text, its
     * first text inside, or its description. Searched inside [tag], as the view that holds Compose is
     * the first focused node of the window.
     */
    fun focusedText(tag: String): String? {
        val focused = device.findObject(By.res(tag))?.findObjects(By.focused(true))?.lastOrNull()
        return focused?.let {
            it.text?.takeIf(String::isNotEmpty)
                ?: it.findObjects(By.text(Pattern.compile(".+"))).firstOrNull()?.text
                ?: it.contentDescription
                ?: it.findObject(By.desc(Pattern.compile(".+")))?.contentDescription
        }
    }

    /**
     * Waits until the focus is in the element tagged [tag]. When nothing in the app has the focus (the
     * screen came later than its first focus, on a slow build), a key of the remote gives it, as on a
     * TV: [nudge] is pressed once, halfway.
     */
    fun waitFocusIn(
        tag: String,
        timeout: Long = TIMEOUT,
        nudge: Int? = null,
    ) {
        if (nudge != null) {
            val focused = runCatching { waitUntil("the focus in $tag", timeout / 2) { focusedIn(tag) } }.isSuccess
            if (focused) return
            if (!device.hasObject(By.pkg(PACKAGE).focused(true))) press(nudge)
        }
        waitUntil("the focus in $tag", timeout) { focusedIn(tag) }
    }

    fun waitUntil(
        what: String,
        timeout: Long = TIMEOUT,
        condition: () -> Boolean,
    ) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return
            SystemClock.sleep(POLL)
        }
        throw AssertionError("Still waiting after $timeout ms for $what\n${screen()}")
    }

    /** Whether the keyboard is on screen. */
    fun keyboardShown(): Boolean = automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    /**
     * Types [text] in the focused field, then the action of the keyboard, as the keyboard would. OK
     * opened the keyboard: a hand types once it is there (`CLAUDE.md`, « Pièges »).
     */
    fun type(
        text: String,
        secret: Boolean = false,
    ) {
        waitUntil("the keyboard") { keyboardShown() }
        // The field of the app, not the focus of the keyboard's window.
        val field =
            device.findObject(By.pkg(PACKAGE).clazz("android.widget.EditText").focused(true))
                ?: throw AssertionError("No field has the focus\n${screen()}")
        field.text = text
        // Refused by the field (seen on the emulator): typed key by key, slowly, as the keyboard
        // would; `input text` sends them faster than the keyboard takes them, and loses some.
        // A password shows as dots: its length says whether it was set.
        if (field.text.orEmpty().length != text.length || (!secret && field.text != text)) typeKeys(text)
        device.pressEnter()
        waitUntil("the keyboard to close") { !keyboardShown() }
    }

    private fun typeKeys(text: String) {
        val keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
        keys.forEach { key ->
            val now = SystemClock.uptimeMillis()
            inject(KeyEvent(now, now, key.action, key.keyCode, 0, key.metaState))
            SystemClock.sleep(KEY_INTERVAL)
        }
    }

    /** What the screen shows, for the message of a failure. */
    fun screen(): String =
        ByteArrayOutputStream()
            .also { device.dumpWindowHierarchy(it) }
            .toString()
            .lineSequence()
            .filter { "resource-id=\"\"" !in it || "text=\"\"" !in it || "focused=\"true\"" in it }
            .take(SCREEN_LINES)
            .joinToString("\n")

    companion object {
        const val TIMEOUT = 15_000L
        const val PAUSE = 300L
        private const val POLL = 100L
        private const val REPEAT_DELAY = 500L
        private const val REPEAT_INTERVAL = 50L
        private const val SCREEN_LINES = 150
        private const val KEY_INTERVAL = 80L
    }
}

/** Starts the app from the launcher and signs in if it has to: the home, « Lire » focused. */
internal fun Remote.launchSignedIn() {
    launch()
    val known = runCatching { waitUntil("a known screen", SIGN_IN_TIMEOUT) { knownScreen() } }.isSuccess
    if (!known) {
        // Left halfway through a sign-in by a run before: from a first launch again.
        device.executeShellCommand("pm clear $PACKAGE")
        launch()
    }
    signInIfNeeded()
}

private fun Remote.launch() {
    device.pressHome()
    device.executeShellCommand("am start -W -n $PACKAGE/$PACKAGE.tv.MainActivity")
}

private fun Remote.knownScreen() = has(HOME_PLAY) || has(START) || has(PROFILE_0)

/**
 * Signs in to the test Jellyfin from wherever the app stands: the first launch (address, profile,
 * password), « Qui regarde ? », or the home already. Ends on the home, « Lire » focused.
 */
internal fun Remote.signInIfNeeded() {
    waitUntil("the first screen", SIGN_IN_TIMEOUT) { knownScreen() }
    if (has(START)) {
        // A first focus: an app started from the shell may still be in touch mode.
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        waitFocusIn(START)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        // No server answers the discovery from the emulator: the address is typed.
        waitFocusIn(ENTER_ADDRESS, DISCOVERY_TIMEOUT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFocusIn(ADDRESS_FIELD)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        type(server)
    }
    if (!has(HOME_PLAY)) {
        // « Qui regarde ? »: « admin » first.
        waitFocusIn(PROFILE_0, SIGN_IN_TIMEOUT, nudge = KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntil("Quick Connect or the home", SIGN_IN_TIMEOUT) { has(USE_PASSWORD) || has(HOME_PLAY) }
        if (has(USE_PASSWORD)) {
            waitFocusIn(USE_PASSWORD)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFocusIn(PASSWORD_FIELD)
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            type(password, secret = true)
        }
    }
    waitHome()
}

/**
 * The home is there with its rows, « Lire » focused. The app opens on « Qui regarde ? » (its default,
 * « Toujours demander »): OK on the first profile, signed in, opens its home.
 */
internal fun Remote.waitHome() {
    val shown = runCatching { waitUntil("the home or the profiles", SIGN_IN_TIMEOUT) { homeOrProfiles() } }.isSuccess
    if (!shown) {
        // « Le serveur ne répond pas » (the test server, loaded, missed an answer): OK on « Réessayer »,
        // which has the focus, as a user would.
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntil("the home or the profiles, after « Réessayer »", SIGN_IN_TIMEOUT) { homeOrProfiles() }
    }
    if (!has(HOME_PLAY)) {
        waitFocusIn(PROFILE_0, nudge = KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_CENTER, pause = 0)
    }
    waitFor(HOME_PLAY, SIGN_IN_TIMEOUT)
    waitFocusIn(HOME_PLAY, SIGN_IN_TIMEOUT)
}

private fun Remote.homeOrProfiles() = has(HOME_PLAY) || has(PROFILE_0)

/** From the home, into the rail and onto the library named [name] (a name given by the server). */
internal fun Remote.openLibrary(name: String) {
    press(KeyEvent.KEYCODE_DPAD_LEFT)
    waitFocusIn(RAIL)
    repeat(RAIL_ENTRIES) {
        // What accessibility says lags the focus a little: wait for it to say the entry.
        val reached = runCatching { waitUntil("$name focused", FOCUS_SETTLE) { focusedText(RAIL) == name } }.isSuccess
        if (reached) {
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFor(LIBRARY_ITEMS, SIGN_IN_TIMEOUT)
            waitFocusIn(LIBRARY_ITEMS, SIGN_IN_TIMEOUT, nudge = KeyEvent.KEYCODE_DPAD_DOWN)
            return
        }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
    }
    throw AssertionError("No entry $name in the rail\n${screen()}")
}

internal const val START = "start"
internal const val ENTER_ADDRESS = "enter-address"
internal const val ADDRESS_FIELD = "address-field"
internal const val PROFILE_0 = "profile-0"
internal const val USE_PASSWORD = "use-password"
internal const val PASSWORD_FIELD = "password-field"
internal const val HOME = "home"
internal const val HOME_PLAY = "home-play"
internal const val RAIL = "rail"
internal const val LIBRARY_ITEMS = "library-items"
internal const val DETAILS = "details"
internal const val RAIL_SETTINGS = "rail-settings"
internal const val SETTINGS = "settings"
internal const val SETTINGS_PAGE = "settings-page"
internal const val SETTINGS_DEVICE = "setting-Device"
internal const val SETTINGS_UPDATE = "setting-update"
internal const val UPDATE = "update"
internal const val PLAYER = "player"
internal const val PLAYER_POSITION = "player-position"
internal const val PLAYER_ERROR = "player-error"

/** The film library of the test Jellyfin (45 films, `ci/jellyfin/generate-media.sh`). */
internal const val FILMS = "Films"

private const val SIGN_IN_TIMEOUT = 30_000L
private const val DISCOVERY_TIMEOUT = 20_000L
private const val RAIL_ENTRIES = 8
private const val FOCUS_SETTLE = 1_500L
