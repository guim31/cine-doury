package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Icon
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

/**
 * OK held on the element (`UI.md` § 2: « un appui long sur OK ouvre le menu contextuel »): the
 * remote repeats the key after about half a second; the first repeat calls [onLongPress] once, and
 * the release that follows is swallowed, so the element is not clicked as well. A short press
 * stays a click. Put it before the focusable part of the element (or on a parent).
 */
@Composable
fun Modifier.onLongPress(onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    val action by rememberUpdatedState(onLongPress)
    val held = remember { BooleanArray(1) }
    return onPreviewKeyEvent { event ->
        if (event.key !in SELECT_KEYS) return@onPreviewKeyEvent false
        when {
            event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0 -> {
                if (!held[0]) {
                    held[0] = true
                    action()
                }
                true
            }

            event.type == KeyEventType.KeyUp && held[0] -> {
                held[0] = false
                true
            }

            else -> {
                false
            }
        }
    }
}

private val SELECT_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * The box of an item in a selection, in the top left corner of its card: ticked in the accent,
 * empty on a veil otherwise.
 */
@Composable
fun SelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CineTheme.colors
    Icon(
        imageVector = if (selected) CineIcons.Checked else CineIcons.Unchecked,
        contentDescription = null,
        tint = if (selected) colors.accent else colors.text,
        modifier =
            modifier
                // Inside the card: TalkBack says whether the card is in the selection.
                .semantics { this.selected = selected }
                .padding(CineSpacing.xs)
                .background(colors.background.copy(alpha = SELECTION_VEIL), MarkShape)
                .size(CineSizes.icon),
    )
}

private val MarkShape = RoundedCornerShape(CineSizes.cardCorner / 2)
private const val SELECTION_VEIL = 0.6f
