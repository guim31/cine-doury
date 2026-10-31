package fr.guim.cinedoury.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What the player says of a wait (`SPEC.md` § 4.29), sampled at each step of its clock:
 * - **N, « Connexion au serveur perdue »** only when loads really fail: the player waits
 *   ([Sample.buffering]) for [RECONNECTING] at least, and the last loads failed
 *   ([Sample.failingLoads]). A stream that starts, a slow server, a playlist the server prepares are
 *   waits, not a lost connection;
 * - **Q, a playback that no longer moves** says it: the player means to play and waits, the position
 *   does not move and no load succeeds, for [STALL] ([Verdict.failing]: loads failed meanwhile, the
 *   connection is lost; else nothing failed at all). Not without a network on the phone
 *   ([Sample.offline]): there, the wait is meant (M2), and the film starts again by itself.
 */
class PlaybackWatch(
    private val reconnectingAfter: Duration = RECONNECTING,
    private val stallAfter: Duration = STALL,
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
        val stalled: Boolean,
        /** Loads were failing: a lost connection rather than a player that stands still. */
        val failing: Boolean,
        /** How long nothing has moved; [Duration.ZERO] while something does. */
        val stalledFor: Duration,
        /** When the last load succeeded, as long ago; null without one since the stream started. */
        val sinceLastLoad: Duration?,
    )

    private var waiting = Duration.ZERO
    private var still = Duration.ZERO
    private var sinceLoad: Duration? = null
    private var lastPosition: Duration? = null
    private var lastLoads = -1

    fun sample(
        step: Duration,
        sample: Sample,
    ): Verdict {
        waiting = if (sample.buffering) waiting + step else Duration.ZERO
        val loaded = sample.loadsCompleted != lastLoads
        sinceLoad =
            when {
                loaded && sample.loadsCompleted > 0 -> Duration.ZERO
                loaded -> null
                else -> sinceLoad?.plus(step)
            }
        val moving = sample.position != lastPosition || loaded
        val stuck = sample.playWhenReady && sample.buffering && !sample.offline && !moving
        still = if (stuck) still + step else Duration.ZERO
        lastPosition = sample.position
        lastLoads = sample.loadsCompleted
        return Verdict(
            reconnecting = sample.buffering && sample.failingLoads > 0 && waiting >= reconnectingAfter,
            stalled = still >= stallAfter,
            failing = sample.failingLoads > 0,
            stalledFor = still,
            sinceLastLoad = sinceLoad,
        )
    }

    /** A new stream, a jump, « Réessayer »: everything starts again. */
    fun reset() {
        waiting = Duration.ZERO
        still = Duration.ZERO
        sinceLoad = null
        lastPosition = null
        lastLoads = -1
    }

    companion object {
        /** The banner waits that long, so that a single failed load does not flash it. */
        val RECONNECTING = 3.seconds

        /**
         * Nothing moved for that long: 30 s, ten segments of Jellyfin (3 s each), two read timeouts of
         * the player (15 s), and well beyond what a server that starts a transcoding takes (a few
         * seconds on the test server). On the TV the retries of the loads (20) would take minutes when
         * each one hangs until its timeout.
         */
        val STALL = 30.seconds
    }
}
