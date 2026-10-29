package fr.guim.cinedoury.benchmark

import android.view.KeyEvent

/**
 * The journeys of the profiles and the benchmarks (`SPEC.md` § 4.16), on the test Jellyfin, from the
 * home with its large tile (or « Lire ») focused. Each ends where it started.
 */
internal object Journeys {
    /** Down the rows of the home, along a row and back, then up to the banner. */
    fun Remote.browseHome() {
        repeat(HOME_ROWS) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        repeat(ROW_CARDS) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        backToFirstCard()
        repeat(HOME_ROWS - 1) { press(KeyEvent.KEYCODE_DPAD_UP) }
        backToStart()
    }

    /**
     * Home → rail → « Films » → the card the grid opens on → its details → back to the home. No blind
     * moves in the grid: it opens on the item left last time (a pass of the profile leaves it
     * anywhere), and its columns follow the size of the interface (essai réel 3: two → went past the
     * last column, onto the A–Z bar). The grid scrolls in [scrollGrid].
     */
    fun Remote.libraryRoundTrip() {
        openLibrary(FILMS)
        waitFocusIn(LIBRARY_ITEMS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(DETAILS)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        press(KeyEvent.KEYCODE_BACK)
        waitFor(LIBRARY_ITEMS)
        press(KeyEvent.KEYCODE_BACK)
        waitHome()
    }

    /** From the home: onto the first card of the first row, ready to open it. */
    fun Remote.toFirstCard() {
        press(KeyEvent.KEYCODE_DPAD_DOWN)
    }

    /** OK on the focused card: its details, then back. */
    fun Remote.openDetails() {
        press(KeyEvent.KEYCODE_DPAD_CENTER, pause = 0)
        waitFor(DETAILS)
        waitFocusIn(DETAILS)
        press(KeyEvent.KEYCODE_BACK)
        waitFor(HOME)
    }

    /** From the home: into the rail, onto « Réglages » (the last entry), ready to open it. */
    fun Remote.toSettingsEntry() {
        press(KeyEvent.KEYCODE_DPAD_LEFT)
        waitFocusIn(RAIL)
        // The last entry; an app before J8 has no tag on it: down to the end of the rail.
        repeat(RAIL_ENTRIES) {
            if (focusedIn(RAIL_SETTINGS)) return
            press(KeyEvent.KEYCODE_DPAD_DOWN)
        }
    }

    /** From a card of the first row of the home, back up to where the home starts. */
    fun Remote.backToBanner() = backToStart()

    /** OK on « Réglages »: the settings, a page, back to the settings and to the home. */
    fun Remote.openSettings() {
        press(KeyEvent.KEYCODE_DPAD_CENTER, pause = 0)
        waitFor(SETTINGS)
        waitFocusIn(SETTINGS)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(SETTINGS_PAGE)
        press(KeyEvent.KEYCODE_BACK)
        waitFor(SETTINGS)
        press(KeyEvent.KEYCODE_BACK)
        waitFor(HOME)
    }

    /**
     * Réglages → Appareil → « Mise à jour de l'appli », then back to the home (the Baseline Profile).
     * By keys, as a hand would: the lines below the fold of a lazy list are not there to be found.
     */
    fun Remote.settingsAndUpdate() {
        toSettingsEntry()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFocusIn(SETTINGS)
        downTo(SETTINGS, SETTINGS_DEVICE)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(SETTINGS_PAGE)
        downTo(SETTINGS_PAGE, SETTINGS_UPDATE)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFor(UPDATE)
        repeat(BACK_TO_HOME) {
            if (has(HOME)) return
            press(KeyEvent.KEYCODE_BACK)
        }
        waitFor(HOME)
    }

    /**
     * ↓ in the list [list] until the line [tag] has the focus, one line at a time: accessibility
     * follows the focus late, and a key pressed before it told the line reached goes past it.
     */
    private fun Remote.downTo(
        list: String,
        tag: String,
    ) {
        repeat(SETTINGS_LINES) {
            if (focusedIn(tag)) return
            val before = focusedText(list)
            press(KeyEvent.KEYCODE_DPAD_DOWN)
            runCatching { waitUntil("the next line", LINE_TIMEOUT) { focusedText(list) != before } }
        }
        waitFocusIn(tag)
    }

    /** In the grid of « Films »: down with the key held, then back up the same way. */
    fun Remote.scrollGrid() {
        hold(KeyEvent.KEYCODE_DPAD_DOWN, HOLD_MILLIS)
        hold(KeyEvent.KEYCODE_DPAD_UP, HOLD_MILLIS)
    }

    private const val HOME_ROWS = 4
    private const val ROW_CARDS = 6
    private const val HOLD_MILLIS = 3_000L
    private const val RAIL_ENTRIES = 12
    private const val BACK_TO_HOME = 4
    private const val SETTINGS_LINES = 20
    private const val LINE_TIMEOUT = 2_000L
}
