package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import fr.guim.cinedoury.core.designsystem.theme.CineSizes

/**
 * Scrolls the lists in [content] so that the focused element stays at a fixed place (`UI.md` § 3):
 * the content moves, not the focus. The element lands [offset] from the start of the list, or at
 * [fraction] of its size when [offset] is null; the list stops at its ends.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusPivot(
    offset: Dp? = null,
    fraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    val offsetPx = offset?.let { with(LocalDensity.current) { it.toPx() } }
    val spec =
        remember(offsetPx, fraction) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float = offset - (offsetPx ?: (containerSize * fraction))
            }
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Scrolls the lists and columns in [content] so that a focused element shows **with its outline and
 * halo** ([CineSizes.focusRoom] around it), not only its own bounds: at the edge of a scrolling form,
 * the focus treatment was cut (J8). An element already in view, room included, does not move.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepFocusRoom(content: @Composable () -> Unit) {
    val room = with(LocalDensity.current) { CineSizes.focusRoom.toPx() }
    val spec =
        remember(room) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(
                    offset: Float,
                    size: Float,
                    containerSize: Float,
                ): Float {
                    val start = offset - room
                    val end = offset + size + room
                    return when {
                        start >= 0f && end <= containerSize -> 0f

                        // Too tall for the room on both sides: its start shows.
                        end - start > containerSize || start < 0f -> start

                        else -> end - containerSize
                    }
                }
            }
        }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
