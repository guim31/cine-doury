package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * What a screen needs to take part in the shared element transitions of the app: the scope of the
 * transition and the visibility of the screen in it. The app provides it around each screen.
 */
@Stable
class SharedElements(
    val transition: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

val LocalSharedElements = staticCompositionLocalOf<SharedElements?> { null }

/**
 * Tags the poster of the item [key]: when a details screen opens, the poster flies from the card to
 * its place in 250 ms (`UI.md` § 7). Nothing outside a transition, without a key, or with reduced
 * motion; it never holds the input back (the screen under it takes the focus at once).
 */
@Composable
fun Modifier.sharedPoster(key: String?): Modifier {
    val shared = LocalSharedElements.current
    if (shared == null || key == null || CineTheme.reducedMotion) return this
    return with(shared.transition) {
        this@sharedPoster.sharedElement(
            sharedContentState = rememberSharedContentState("poster-$key"),
            animatedVisibilityScope = shared.visibility,
            boundsTransform = PosterFlight,
        )
    }
}

private val PosterFlight =
    BoundsTransform { _, _ -> tween(CineMotion.DETAIL_OPEN_MILLIS, easing = CineMotion.Standard) }
