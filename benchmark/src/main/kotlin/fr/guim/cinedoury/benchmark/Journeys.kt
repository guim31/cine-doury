package fr.guim.cinedoury.benchmark

import android.view.KeyEvent

/**
 * The journeys of the profiles and the benchmarks (`SPEC.md` § 4.16), on the test Jellyfin, from the
 * home with « Lire » focused. Each ends where it started.
 */
internal object Journeys {
    /** Down the rows of the home, along a row and back, then up to the banner. */
    fun Remote.browseHome() {
        repeat(HOME_ROWS) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        repeat(ROW_CARDS) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
        repeat(ROW_CARDS) { press(KeyEvent.KEYCODE_DPAD_LEFT) }
        repeat(HOME_ROWS) { press(KeyEvent.KEYCODE_DPAD_UP) }
        waitFocusIn(HOME_PLAY)
    }

    /** Home → rail → « Films » → a card of the grid → its details → back to the home. */
    fun Remote.libraryRoundTrip() {
        openLibrary(FILMS)
        repeat(GRID_ROWS) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
        repeat(GRID_COLUMNS) { press(KeyEvent.KEYCODE_DPAD_RIGHT) }
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

    /** From a card of the first row of the home, back up to « Lire ». */
    fun Remote.backToBanner() {
        press(KeyEvent.KEYCODE_DPAD_UP)
        waitFocusIn(HOME_PLAY)
    }

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

    /** Réglages → Appareil → « Mise à jour de l'appli », then back to the home (the Baseline Profile). */
    fun Remote.settingsAndUpdate() {
        toSettingsEntry()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        waitFocusIn(SETTINGS)
        val device = waitFor(SETTINGS_DEVICE)
        device.click()
        waitFor(SETTINGS_PAGE)
        waitFor(SETTINGS_UPDATE).click()
        waitFor(UPDATE)
        repeat(BACK_TO_HOME) {
            if (has(HOME)) return
            press(KeyEvent.KEYCODE_BACK)
        }
        waitFor(HOME)
    }

    /** In the grid of « Films »: down with the key held, then back up the same way. */
    fun Remote.scrollGrid() {
        hold(KeyEvent.KEYCODE_DPAD_DOWN, HOLD_MILLIS)
        hold(KeyEvent.KEYCODE_DPAD_UP, HOLD_MILLIS)
    }

    private const val HOME_ROWS = 4
    private const val ROW_CARDS = 6
    private const val GRID_ROWS = 3
    private const val GRID_COLUMNS = 2
    private const val HOLD_MILLIS = 3_000L
    private const val RAIL_ENTRIES = 12
    private const val BACK_TO_HOME = 4
}
