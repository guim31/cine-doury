package fr.guim.cinedoury.core.playback

import kotlin.math.abs
import kotlin.math.roundToInt

/** A mode of the screen: its size and refresh rate. */
data class ScreenMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

/**
 * Frame rate matching (`FONCTIONNALITES.md` § 5, « Image »): the mode of the same size whose refresh
 * rate is the frame rate of the film, else its double (47.95 Hz for 23.976 p), else its triple…;
 * null when no mode fits, or when the current one already does. 24p is not shown at 60 Hz, which
 * judders (3:2 pulldown), when the screen offers 24 Hz.
 */
object DisplayModes {
    private const val TOLERANCE = 0.01f
    private const val MAX_MULTIPLE = 5

    /**
     * The mode to ask for, or null to keep [current].
     *
     * @param frameRate the cadence of the film; 0 (or less) leaves the refresh rate alone.
     * @param videoHeight with « Résolution adaptée » (`SPEC.md` § 4.16), the height of the film: the
     * smallest size of the screen that holds it (1080p for a 1080p film on a 4K TV), the TV then
     * scales it up itself; null keeps the size of [current].
     */
    fun choose(
        modes: List<ScreenMode>,
        current: ScreenMode,
        frameRate: Float,
        videoHeight: Int? = null,
    ): ScreenMode? {
        val size = videoHeight?.let { targetSize(modes, it) } ?: (current.width to current.height)
        val sameSize = modes.filter { (it.width to it.height) == size }
        val byRate =
            sameSize
                .mapNotNull { mode -> multipleOf(mode.refreshRate, frameRate)?.let { mode to it } }
                .minWithOrNull(compareBy({ it.second }, { abs(it.first.refreshRate / frameRate - it.second) }))
                ?.first
        return if (size != current.width to current.height) {
            // Another size: at the cadence of the film, else at the refresh rate closest to the current one.
            byRate ?: sameSize.minByOrNull { abs(it.refreshRate - current.refreshRate) }
        } else {
            byRate?.takeIf { multipleOf(current.refreshRate, frameRate) != multipleOf(it.refreshRate, frameRate) }
        }
    }

    /** The smallest size of the screen at least as tall as the film, else the tallest. */
    private fun targetSize(
        modes: List<ScreenMode>,
        videoHeight: Int,
    ): Pair<Int, Int>? {
        val sizes = modes.map { it.width to it.height }.distinct()
        return sizes.filter { it.second >= videoHeight }.minByOrNull { it.second } ?: sizes.maxByOrNull { it.second }
    }

    /** n when [refreshRate] is n × [frameRate] (within 1 %), up to 5. */
    private fun multipleOf(
        refreshRate: Float,
        frameRate: Float,
    ): Int? {
        if (frameRate <= 0f) return null
        val ratio = refreshRate / frameRate
        val multiple = ratio.roundToInt()
        return multiple.takeIf { it in 1..MAX_MULTIPLE && abs(ratio - it) <= TOLERANCE * it }
    }
}
