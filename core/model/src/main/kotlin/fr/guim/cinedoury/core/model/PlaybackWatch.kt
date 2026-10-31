package fr.guim.cinedoury.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the player says of a wait (`SPEC.md` § 4.29), sampled at each step of its clock.
 *
 * **N, « Connexion au serveur perdue »** only when loads really fail: the player waits
 * ([Sample.buffering]) for [RECONNECTING] at least, and the last loads failed
 * ([Sample.failingLoads]). A stream that starts, a slow server, a playlist the server prepares are
 * waits, not a lost connection.
 */
class PlaybackWatch(
    private val reconnectingAfter: Duration = RECONNECTING,
) {
    /** One look at the player. */
    data class Sample(
        val position: Duration,
        val buffering: Boolean,
        val playWhenReady: Boolean,
        val loadsCompleted: Int,
        val failingLoads: Int,
        val offline: Boolean = false,
    )

    data class Verdict(
        val reconnecting: Boolean,
    )

    private var waiting = Duration.ZERO

    fun sample(
        step: Duration,
        sample: Sample,
    ): Verdict {
        waiting = if (sample.buffering) waiting + step else Duration.ZERO
        return Verdict(
            reconnecting = sample.buffering && sample.failingLoads > 0 && waiting >= reconnectingAfter,
        )
    }

    /** A new stream, « Réessayer »: everything starts again. */
    fun reset() {
        waiting = Duration.ZERO
    }

    companion object {
        /** The banner waits that long, so that a single failed load does not flash it. */
        val RECONNECTING = 3.seconds
    }
}
