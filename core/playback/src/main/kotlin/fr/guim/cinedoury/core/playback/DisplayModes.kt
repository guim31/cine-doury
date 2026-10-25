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

    fun choose(
        modes: List<ScreenMode>,
        current: ScreenMode,
        frameRate: Float,
    ): ScreenMode? {
        if (frameRate <= 0f) return null
        val sameSize = modes.filter { it.width == current.width && it.height == current.height }
        val best =
            sameSize
                .mapNotNull { mode -> multipleOf(mode.refreshRate, frameRate)?.let { mode to it } }
                .minWithOrNull(compareBy({ it.second }, { abs(it.first.refreshRate / frameRate - it.second) }))
                ?.first ?: return null
        return best.takeIf { multipleOf(current.refreshRate, frameRate) != multipleOf(best.refreshRate, frameRate) }
    }

    /** n when [refreshRate] is n × [frameRate] (within 1 %), up to 5. */
    private fun multipleOf(
        refreshRate: Float,
        frameRate: Float,
    ): Int? {
        val ratio = refreshRate / frameRate
        val multiple = ratio.roundToInt()
        return multiple.takeIf { it in 1..MAX_MULTIPLE && abs(ratio - it) <= TOLERANCE * it }
    }
}
