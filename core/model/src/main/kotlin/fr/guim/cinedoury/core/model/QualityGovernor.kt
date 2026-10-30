package fr.guim.cinedoury.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** What the network looks like to the phone, for its default quality (M2, `SPEC.md` § 4.27). */
enum class NetworkKind {
    /** Wi-Fi or Ethernet: « Wi-Fi » (Automatique by default). */
    Wifi,

    /** A mobile network, or any network metered: « Réseau mobile » (4 Mb/s by default). */
    Mobile,
}

/** A change of quality the [QualityGovernor] asks for: null is the original (« Automatique »). */
data class QualityChange(
    val maxBitrate: Int?,
    val down: Boolean,
)

/**
 * The quality of the phone follows its network (M2, `SPEC.md` § 4.27): the margin only grows when the
 * network is faster than the video. When it melts for a minute while the network answers, the quality
 * goes one step down ([Qualities.BITRATES]); when it has stayed full for two minutes and the network
 * carries one and a half times the step above, it goes one step up, never beyond [ceiling] (the
 * default quality of the settings). An outage is not slowness: while the fetches fail, the margin
 * melts as it should, and nothing changes. Never used when the user chose a quality.
 *
 * @param ceiling the default quality for this network; null for the original.
 * @param sourceBitrate the bitrate of the file, for the first step down from the original.
 */
class QualityGovernor(
    private val ceiling: Int?,
    private val sourceBitrate: Int?,
    current: Int? = ceiling,
) {
    /** The quality asked for now; null for the original. */
    var current: Int? = current
        private set

    private val window = ArrayDeque<Pair<Duration, Duration>>()
    private var fullSince: Duration? = null
    private var quietUntil = Duration.ZERO

    /**
     * One look at the margin, about every second: [now] is a monotonic clock, [throughput] what the
     * fetch measured (bits per second).
     */
    fun sample(
        now: Duration,
        margin: MarginStatus,
        playing: Boolean,
        throughput: Long?,
    ): QualityChange? {
        if (!playing || margin.offline) {
            window.clear()
            fullSince = null
            return null
        }
        window.addLast(now to margin.ahead)
        while (window.size > 1 && now - window.first().first > WINDOW) window.removeFirst()
        fullSince = if (margin.full) fullSince ?: now else null
        if (now < quietUntil) return null
        return down(now, margin) ?: up(now, throughput)
    }

    private fun down(
        now: Duration,
        margin: MarginStatus,
    ): QualityChange? {
        val (since, before) = window.first()
        val melting = now - since >= WINDOW - TOLERANCE && before - margin.ahead >= MELT && margin.ahead < LOW
        if (!melting) return null
        val from = current ?: sourceBitrate ?: Qualities.BITRATES.first()
        val lower = Qualities.BITRATES.firstOrNull { it < from } ?: return null
        return change(QualityChange(lower, down = true), now)
    }

    @Suppress("ReturnCount") // Each condition that keeps the quality where it is.
    private fun up(
        now: Duration,
        throughput: Long?,
    ): QualityChange? {
        val from = current ?: return null
        if (from == ceiling) return null
        val since = fullSince ?: return null
        if (now - since < UP_AFTER || throughput == null) return null
        val step = Qualities.BITRATES.lastOrNull { it > from }
        val target =
            when {
                step == null -> ceiling
                ceiling != null && step >= ceiling -> ceiling
                ceiling == null && sourceBitrate != null && step >= sourceBitrate -> null
                else -> step
            }
        val needed = target ?: sourceBitrate ?: step ?: return null
        if (throughput < needed * UP_MARGIN) return null
        return change(QualityChange(target, down = false), now)
    }

    private fun change(
        change: QualityChange,
        now: Duration,
    ): QualityChange {
        current = change.maxBitrate
        window.clear()
        fullSince = null
        quietUntil = now + if (change.down) QUIET_AFTER_DOWN else QUIET_AFTER_UP
        return change
    }

    companion object {
        /** How long the margin must melt before the quality goes down. */
        val WINDOW = 60.seconds
        private val TOLERANCE = 2.seconds

        /** It melted by this much over [WINDOW]: about a quarter of what played. */
        val MELT = 15.seconds

        /** Above this, a melting margin still has time: the network may come back to speed. */
        val LOW = 3.minutes

        /** How long the margin stays full before the quality goes up. */
        val UP_AFTER = 2.minutes
        const val UP_MARGIN = 1.5
        private val QUIET_AFTER_DOWN = 1.minutes
        private val QUIET_AFTER_UP = 5.minutes

        /** The default quality on a mobile network: 4 Mb/s, 720p in H.264 (`SPEC.md` § 4.27). */
        const val MOBILE_DEFAULT = 4_000_000
    }
}
