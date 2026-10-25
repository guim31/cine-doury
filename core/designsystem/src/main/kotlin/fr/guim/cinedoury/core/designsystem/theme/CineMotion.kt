package fr.guim.cinedoury.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween

/** Durations and curves of `UI.md` § 3 and § 7. */
object CineMotion {
    /** Standard deceleration curve: an element arriving. */
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Standard acceleration curve: an element leaving. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Standard curve: an element moving from one place to another. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val FOCUS_IN_MILLIS = 150
    const val FOCUS_OUT_MILLIS = 120
    const val BACKDROP_FADE_MILLIS = 300
    const val BACKDROP_DELAY_MILLIS = 250
    const val DETAIL_OPEN_MILLIS = 250
    const val PANEL_MILLIS = 200
    const val OSD_FADE_MILLIS = 200
    const val OSD_HIDE_AFTER_MILLIS = 4_000
    const val IMAGE_FADE_MILLIS = 200

    /** Focus arriving (150 ms, deceleration) or leaving (120 ms, acceleration). */
    fun <T> focus(gained: Boolean, reduced: Boolean = false): AnimationSpec<T> =
        when {
            reduced -> snap()
            gained -> tween(FOCUS_IN_MILLIS, easing = Decelerate)
            else -> tween(FOCUS_OUT_MILLIS, easing = Accelerate)
        }

    /** Panels and menus: slide and fade, 200 ms. */
    fun <T> panel(reduced: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else tween(PANEL_MILLIS, easing = Standard)

    /** Plain fade (still played with reduced motion). */
    fun <T> fade(durationMillis: Int, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis = delayMillis, easing = Standard)
}
