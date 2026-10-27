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
}
