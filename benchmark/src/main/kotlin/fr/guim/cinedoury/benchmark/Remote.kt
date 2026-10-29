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

    /** The index of the focused card of a row of the home (tagged `card-<row>-<index>`), if one has it. */
    fun focusedCardIndex(): Int? =
        device
            .findObjects(By.res(CARD))
            .firstOrNull { it.isFocused || it.hasObject(By.focused(true)) }
            ?.resourceName
            ?.substringAfterLast('-')
            ?.toIntOrNull()

    /**
     * The label of the focused element inside the element tagged [tag] (a rail entry): its text, its
     * first text inside, or its description (where a rail entry says its name, essai réel 5). Searched
     * inside [tag], as the view that holds Compose is the first focused node of the window.
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
    fun screen(): String {
        val lines =
            ByteArrayOutputStream()
                .also { device.dumpWindowHierarchy(it) }
                .toString()
                .lines()
        // The focused nodes first: a large screen fills the first lines before reaching them (essai
        // réel 3, the grid of a library under its bar).
        val focused = lines.filter { "focused=\"true\"" in it }.map { "FOCUSED ${it.trim()}" }
        return (
            focused +
                lines
                    .asSequence()
                    .filter { "resource-id=\"\"" !in it || "text=\"\"" !in it || "focused=\"true\"" in it }
                    .take(SCREEN_LINES)
        ).joinToString("\n")
    }

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

/** Starts the app from the launcher and signs in if it has to: the home, where it starts focused. */
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

private fun Remote.knownScreen() = onHome() || has(START) || has(PROFILE_0)

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
    if (!onHome()) {
        // « Qui regarde ? »: « admin » first.
        waitFocusIn(PROFILE_0, SIGN_IN_TIMEOUT, nudge = KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitUntil("Quick Connect or the home", SIGN_IN_TIMEOUT) { has(USE_PASSWORD) || onHome() }
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
 * The home is there with its rows, its large tile (or « Lire ») focused. The app opens on « Qui
 * regarde ? » (its default, « Toujours demander »): OK on the first profile, signed in, opens its home.
 */
internal fun Remote.waitHome() {
    val shown = runCatching { waitUntil("the home or the profiles", SIGN_IN_TIMEOUT) { homeOrProfiles() } }.isSuccess
    if (!shown) {
        if (!device.hasObject(By.pkg(PACKAGE))) {
            // The app did not come to the front (seen on the emulator after a few iterations): OK
            // would open something of the launcher. Started again.
            launch()
        } else {
            // « Le serveur ne répond pas » (the test server, loaded, missed an answer): OK on
            // « Réessayer », which has the focus, as a user would.
            press(KeyEvent.KEYCODE_DPAD_CENTER)
        }
        waitUntil("the home or the profiles, once more", SIGN_IN_TIMEOUT) { homeOrProfiles() }
    }
    if (!onHome()) {
        waitFocusIn(PROFILE_0, nudge = KeyEvent.KEYCODE_DPAD_LEFT)
        press(KeyEvent.KEYCODE_DPAD_CENTER, pause = 0)
    }
    waitUntil("the home", SIGN_IN_TIMEOUT) { onHome() }
    waitFocusIn(homeStart(), SIGN_IN_TIMEOUT)
}

private fun Remote.homeOrProfiles() = onHome() || has(PROFILE_0)

/** The home is there: its mosaic « Ce soir » (essai réel 5), or its banner when the mosaic is off. */
internal fun Remote.onHome(): Boolean = has(TONIGHT_MAIN) || has(HOME_PLAY)

/** Where the home starts: the large tile of the mosaic, else « Lire » of the banner. */
internal fun Remote.homeStart(): String = if (has(TONIGHT_MAIN)) TONIGHT_MAIN else HOME_PLAY

/**
 * From the first row of the home, back up to where it starts: the mosaic comes back with a tile
 * focused, then ← along its line to the large one (← from the large one would open the rail).
 */
internal fun Remote.backToStart() {
    press(KeyEvent.KEYCODE_DPAD_UP)
    waitUntil("the top of the home") { focusedIn(TONIGHT) || focusedIn(HOME_PLAY) }
    repeat(TONIGHT_COLUMNS) {
        if (focusedIn(homeStart())) return
        press(KeyEvent.KEYCODE_DPAD_LEFT)
    }
    waitFocusIn(homeStart())
}

/**
 * Along a row of the home, ← back to its first card, one card at a time: a row can hold fewer cards
 * than the journey went right (the rows left once « Ce soir » took « Reprendre » and « À suivre »,
 * essai réel 5), and a blind ← too many opens the rail.
 */
internal fun Remote.backToFirstCard() {
    var index = focusedCardIndex() ?: return
    while (index > 0) {
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        val target = index - 1
        // What accessibility says lags the focus a little.
        waitUntil("card $target focused", FOCUS_SETTLE) { focusedCardIndex() == target }
        index = target
    }
}

/** From the home, into the rail and onto the library named [name] (a name given by the server). */
internal fun Remote.openLibrary(name: String) {
    press(KeyEvent.KEYCODE_DPAD_LEFT)
    waitFocusIn(RAIL)
    var left = RAIL_ENTRIES
    while (left-- > 0) {
        // What accessibility says lags the focus a little: wait for it to say the entry.
        val reached = runCatching { waitUntil("$name focused", FOCUS_SETTLE) { focusedText(RAIL) == name } }.isSuccess
        if (reached) {
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            waitFor(LIBRARY_ITEMS, SIGN_IN_TIMEOUT)
            waitFocusIn(LIBRARY_ITEMS, SIGN_IN_TIMEOUT, nudge = KeyEvent.KEYCODE_DPAD_DOWN)
            return
        }
        // The bottom of the rail: the library is not below.
        if (focusedIn(RAIL_SETTINGS)) break
        press(KeyEvent.KEYCODE_DPAD_DOWN)
    }
    throw AssertionError("No entry $name in the rail (last read: ${focusedText(RAIL)})\n${screen()}")
}

internal const val START = "start"
internal const val ENTER_ADDRESS = "enter-address"
internal const val ADDRESS_FIELD = "address-field"
internal const val PROFILE_0 = "profile-0"
internal const val USE_PASSWORD = "use-password"
internal const val PASSWORD_FIELD = "password-field"
internal const val HOME = "home"
internal const val HOME_PLAY = "home-play"

/** The mosaic « Ce soir » and its large tile, the first focus of the home (essai réel 5). */
internal const val TONIGHT = "home-tonight"
internal const val TONIGHT_MAIN = "tonight-0"
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
private const val RAIL_ENTRIES = 12
private const val FOCUS_SETTLE = 1_500L

/** A card of a row of the home: `card-<row>-<index>`. */
private val CARD = Pattern.compile("card-.+-\\d+")

/** The small tiles of a line of the mosaic, left of which the large one stands. */
private const val TONIGHT_COLUMNS = 2
