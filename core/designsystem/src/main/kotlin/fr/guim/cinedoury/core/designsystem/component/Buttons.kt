package fr.guim.cinedoury.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import fr.guim.cinedoury.core.designsystem.theme.CineMotion
import fr.guim.cinedoury.core.designsystem.theme.CineScale
import fr.guim.cinedoury.core.designsystem.theme.CineSizes
import fr.guim.cinedoury.core.designsystem.theme.CineSpacing
import fr.guim.cinedoury.core.designsystem.theme.CineTheme

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * The main action of a screen (`UI.md` § 1, principle 5): accent background, the only accent
 * button on the screen. It takes the focus when the screen opens.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = CineTheme.colors
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier =
            modifier
                .height(CineSizes.buttonHeight)
                .withFocusRequester(focusRequester)
                .cineFocusable(focus, PillShape, CineScale.BUTTON, onClick, interactionSource = interactionSource)
                .background(if (pressed) colors.accentPressed else colors.accent)
                .padding(start = if (icon != null) CineSpacing.m else CineSpacing.l, end = CineSpacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CineSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(CineSizes.icon),
            )
        }
        Text(text = text, style = CineTheme.typography.label, color = colors.onAccent, maxLines = 1)
    }
}

/**
 * A secondary action: a round icon button on `surface-2`, whose [label] appears under it only
 * while it has the focus (`UI.md` § 8, « Fiche »). The label does not take part in the layout
 * width, so a row of buttons keeps its rhythm.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    focus: CineFocus = rememberCineFocus(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = CineTheme.colors
    val focused = focus.isFocused
    val labelAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = CineMotion.fade(if (focused) CineMotion.FOCUS_IN_MILLIS else CineMotion.FOCUS_OUT_MILLIS),
        label = "label",
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(CineSizes.roundButton)
                    .withFocusRequester(focusRequester)
                    .cineFocusable(focus, CircleShape, CineScale.BUTTON, onClick, interactionSource = interactionSource)
                    .background(if (focused) colors.text else colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint =
                    when {
                        focused -> colors.background
                        selected -> colors.accent
                        else -> colors.text
                    },
                modifier = Modifier.size(CineSizes.icon),
            )
        }
        Text(
            text = label,
            style = CineTheme.typography.caption,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier =
                Modifier
                    .padding(top = CineSpacing.s)
                    .ignoreWidth()
                    .graphicsLayer { alpha = labelAlpha },
        )
    }
}

/** Attaches [requester] to the focusable element itself, so that `requestFocus()` lands on it. */
private fun Modifier.withFocusRequester(requester: FocusRequester?): Modifier =
    if (requester == null) this else focusRequester(requester)

/** Lays the element out at its own width but reports a zero width to its parent. */
private fun Modifier.ignoreWidth(): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE))
        layout(0, placeable.height) {
            placeable.place(-placeable.width / 2, 0)
        }
    }
