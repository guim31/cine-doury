package fr.guim.cinedoury.feature.player

import android.util.Log
import fr.guim.cinedoury.core.playback.VideoPlayer

/**
 * The players alive in the app (Y, `SPEC.md` § 4.32): one at a time. A player made while another is
 * still alive releases that one (its screen, if it comes back, starts again where it was). Each player
 * made and released writes a line to logcat (tag `CineDoury`), so that a sound that goes on after the
 * player can be read with `adb logcat -s CineDoury`.
 */
object LivePlayers {
    private class Entry(
        val number: Int,
        val player: VideoPlayer,
        val evict: () -> Unit,
    )

    private var current: Entry? = null
    private var made = 0

    /** Where the lines go: logcat, silent in JVM tests; a test may read them. */
    @Volatile
    var sink: (String) -> Unit = { line -> runCatching { Log.i(TAG, line) } }

    /** How many players are alive: 0 or 1. */
    val alive: Int get() = if (current == null) 0 else 1

    /**
     * [player] was just made for [what]; [evict] releases it if another player is made before it is
     * released. Returns its number, for [released].
     */
    @Synchronized
    fun created(
        player: VideoPlayer,
        what: String,
        evict: () -> Unit,
    ): Int {
        val previous = current
        made += 1
        current = Entry(made, player, evict)
        if (previous != null && previous.player !== player) {
            sink("player ${previous.number} still alive when player $made is made: released")
            previous.evict()
        }
        sink("player $made created: $what")
        return made
    }

    @Synchronized
    fun released(
        number: Int,
        why: String,
    ) {
        if (current?.number == number) current = null
        sink("player $number released: $why")
    }

    private const val TAG = "CineDoury"
}
