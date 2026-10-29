package fr.guim.cinedoury.benchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The optimised APK works (`SPEC.md` § 4.16): R8 removes and rewrites code, and a missing rule shows
 * only at run time (serialisation, reflection, injection). Run by `ci/emulator-release.sh` against
 * the APK the CI publishes, installed over the debug one.
 */
@RunWith(AndroidJUnit4::class)
class OptimisedApkTest {
    private val remote = Remote()

    /**
     * Installed over the debug APK, signed in by `SignInJourneyTest`: the app opens on « Qui regarde ? »
     * and the profile on its home, without signing in again (same key, same data: Keystore,
     * DataStore, Room).
     */
    @Test
    fun keepsTheSessionOfTheDebugApk() {
        remote.device.pressHome()
        remote.device.executeShellCommand("am start -W -n $PACKAGE/$PACKAGE.tv.MainActivity")
        remote.waitUntil("the profiles or the home", PLAYER_TIMEOUT) { remote.has(PROFILE_0) || remote.onHome() }
        if (remote.has(START)) throw AssertionError("The server is forgotten\n${remote.screen()}")
        if (remote.has(PROFILE_0)) {
            remote.waitFocusIn(PROFILE_0)
            remote.press(KeyEvent.KEYCODE_DPAD_CENTER)
            // Still signed in: no password, no Quick Connect.
            remote.waitUntil("the home", PLAYER_TIMEOUT) { remote.onHome() || remote.has(USE_PASSWORD) }
            if (remote.has(USE_PASSWORD)) throw AssertionError("The session is lost\n${remote.screen()}")
        }
        remote.waitHome()
    }

    /** From a first launch (the script clears the app) to a film that plays, then a new start. */
    @Test
    fun signsInAndPlays() {
        remote.launchSignedIn()
        remote.press(KeyEvent.KEYCODE_DPAD_CENTER)
        remote.waitFor(PLAYER, PLAYER_TIMEOUT)
        // The film plays: its position moves past 0:00, and no error shows.
        val end = SystemClock.uptimeMillis() + PLAYER_TIMEOUT
        var position: String? = null
        while (position.isNullOrEmpty() || position.none { it in '1'..'9' }) {
            if (remote.has(PLAYER_ERROR)) throw AssertionError("The player shows an error\n${remote.screen()}")
            if (SystemClock.uptimeMillis() > end) {
                throw AssertionError("The position stays at $position\n${remote.screen()}")
            }
            // A short press shows the controls, with the position.
            if (!remote.has(PLAYER_POSITION)) remote.press(KeyEvent.KEYCODE_DPAD_UP)
            position = remote.device.findObject(By.res(PLAYER_POSITION))?.text
            SystemClock.sleep(POLL)
        }
        // « Retour » closes what is open (a panel, the controls), then leaves the player.
        repeat(BACKS) { if (remote.has(PLAYER)) remote.press(KeyEvent.KEYCODE_BACK, pause = BACK_PAUSE) }
        remote.waitHome()
        // Started again, the app is still signed in.
        remote.device.executeShellCommand("am force-stop $PACKAGE")
        remote.device.executeShellCommand("am start -W -n $PACKAGE/$PACKAGE.tv.MainActivity")
        remote.waitHome()
    }

    private companion object {
        const val PLAYER_TIMEOUT = 45_000L
        const val POLL = 500L
        const val BACK_PAUSE = 1_000L
        const val BACKS = 4
    }
}
